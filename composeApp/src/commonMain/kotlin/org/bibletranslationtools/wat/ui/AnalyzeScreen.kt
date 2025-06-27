package org.bibletranslationtools.wat.ui

import ComboBox
import Option
import OptionIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.SingletonWord
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.Model
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.format
import org.bibletranslationtools.wat.ui.control.BatchInfo
import org.bibletranslationtools.wat.ui.control.BatchProgress
import org.bibletranslationtools.wat.ui.control.ExtraAction
import org.bibletranslationtools.wat.ui.control.PageType
import org.bibletranslationtools.wat.ui.control.SingletonCard
import org.bibletranslationtools.wat.ui.control.SingletonRow
import org.bibletranslationtools.wat.ui.control.StatusBox
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.delete_batch
import wordanalysistool.composeapp.generated.resources.likely_correct
import wordanalysistool.composeapp.generated.resources.likely_incorrect
import wordanalysistool.composeapp.generated.resources.logout
import wordanalysistool.composeapp.generated.resources.names
import wordanalysistool.composeapp.generated.resources.process_words
import wordanalysistool.composeapp.generated.resources.review_needed
import wordanalysistool.composeapp.generated.resources.save_report
import wordanalysistool.composeapp.generated.resources.sort_by_alphabet
import wordanalysistool.composeapp.generated.resources.sort_by_alphabet_desc
import wordanalysistool.composeapp.generated.resources.sort_by_reviewed
import wordanalysistool.composeapp.generated.resources.sort_words

class AnalyzeScreen(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: List<Verse>,
    private val user: User
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<AnalyzeViewModel> {
            parametersOf(language, resourceType, verses, user)
        }

        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AnalyzeEvent.Idle)
        var selectedWord by remember { mutableStateOf<SingletonWord?>(null) }

        val modelsState = Model.entries.mapNotNull {
            val active = rememberBooleanSetting(it.value, false).value
            if (active) it.value else null
        }.toMutableStateList()
        val models = remember { modelsState }

        val apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        var wordsSorting by rememberStringSetting(
            Settings.SORT_WORDS.name,
            WordsSorting.ALPHABET.name
        )
        val sorting = try {
            WordsSorting.valueOf(wordsSorting)
        } catch (_: Exception) {
            WordsSorting.ALPHABET
        }

        var filteredSingletons by remember { mutableStateOf(state.singletons) }

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        val wordsListState = rememberLazyListState()
        val statuses = remember { mutableStateListOf<String>() }

        var showStatuses by remember { mutableStateOf(false) }

        val localizedSorting = WordsSorting.entries.associateWith { localizeSorting(it) }
        var adminActions by remember { mutableStateOf<List<ExtraAction>>(emptyList()) }

        LaunchedEffect(event) {
            when (event) {
                is AnalyzeEvent.WordsSorted -> {
                    wordsListState.animateScrollToItem(0)
                    viewModel.onEvent(AnalyzeEvent.Idle)
                }
                is AnalyzeEvent.Logout -> {
                    accessToken = null
                    navigator.popUntilRoot()
                }
                is AnalyzeEvent.UpdateSelectedWord -> {
                    val correct = (event as AnalyzeEvent.UpdateSelectedWord).value
                    selectedWord = selectedWord?.copy(correct = correct)
                }
                is AnalyzeEvent.RefreshSelectedWord -> {
                    selectedWord = state.singletons.find { it.word == selectedWord?.word }
                    viewModel.onEvent(AnalyzeEvent.Idle)
                }
                else -> Unit
            }
        }

        LaunchedEffect(models) {
            if (models.isNotEmpty()) {
                viewModel.onEvent(AnalyzeEvent.UpdateModels(models))
            }
        }

        LaunchedEffect(apostropheIsSeparator) {
            viewModel.onEvent(AnalyzeEvent.FindSingletons(apostropheIsSeparator))
        }

        LaunchedEffect(state.singletons, wordsSorting) {
            filteredSingletons = filterSingletons(state.singletons, sorting)
        }

        LaunchedEffect(state.status) {
            state.status?.let {
                val time = Clock.System.now().toLocalDateTime(
                    TimeZone.currentSystemDefault()
                )
                if (statuses.size > 1_000) {
                    statuses.removeAt(0)
                }
                statuses.add("${time.format()} $it")
            }
        }

        LaunchedEffect(user) {
            val processWordsText = getString(Res.string.process_words)
            val deleteBatchText = getString(Res.string.delete_batch)

            adminActions = if (user.admin) {
                listOf(
                    ExtraAction(
                        title = processWordsText,
                        icon = Icons.Default.Sync,
                        onClick = {
                            viewModel.onEvent(AnalyzeEvent.BatchWords)
                        }
                    ),
                    ExtraAction(
                        title = deleteBatchText,
                        icon = Icons.Default.Delete,
                        onClick = {
                            viewModel.onEvent(AnalyzeEvent.DeleteBatch)
                        }
                    )
                )
            } else emptyList()
        }

        Scaffold(
            topBar = {
                TopNavigationBar(
                    title = "[${language.ietfCode}] ${language.name} - $resourceType",
                    user = user,
                    page = PageType.ANALYZE,
                    extraAction = (adminActions + listOf(
                        ExtraAction(
                            title = stringResource(Res.string.save_report),
                            icon = Icons.Default.Save,
                            onClick = {
                                viewModel.onEvent(AnalyzeEvent.SaveReport)
                            }
                        ),
                        ExtraAction(
                            title = stringResource(Res.string.logout),
                            icon = Icons.AutoMirrored.Filled.Logout,
                            onClick = {
                                accessToken = null
                                navigator.popUntilRoot()
                            }
                        )
                    )).toTypedArray()
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                            .padding(16.dp)
                            .weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.shadow(4.dp, RoundedCornerShape(8.dp))
                                .weight(0.3f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    BatchInfo(singletons = state.singletons)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (state.batchProgress >= 0) {
                                        BatchProgress(
                                            progress = state.batchProgress,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ComboBox(
                                        value = sorting,
                                        options = WordsSorting.entries.map { sortingToOption(it) },
                                        onOptionSelected = { sort: WordsSorting ->
                                            wordsSorting = sort.name
                                        },
                                        valueConverter = { sort ->
                                            localizedSorting[sort] ?: ""
                                        },
                                        label = stringResource(Res.string.sort_words)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                LazyColumn(state = wordsListState) {
                                    items(items = filteredSingletons, key = { it.word }) { singleton ->
                                        SingletonRow(
                                            singleton = singleton,
                                            selected = selectedWord == singleton,
                                            direction = language.direction,
                                            onSelect = { selectedWord = singleton }
                                        )
                                    }
                                }
                            }
                        }

                        selectedWord?.let { word ->
                            SingletonCard(
                                word = word,
                                onAnswer = {
                                    viewModel.onEvent(
                                        AnalyzeEvent.UpdateCorrect(word.word, it)
                                    )
                                },
                                modifier = Modifier.weight(0.7f)
                            )
                        } ?: Spacer(modifier = Modifier.weight(0.7f))
                    }

                    HorizontalDivider()

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .height(36.dp)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = state.status ?: "",
                            fontSize = 12.sp
                        )
                        IconButton(onClick = { showStatuses = !showStatuses }) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null)
                        }
                    }
                }

                if (showStatuses) {
                    StatusBox(
                        statuses = statuses,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            state.alert?.let {
                AlertDialog(
                    message = it.message,
                    onDismiss = it.onClosed
                )
            }

            state.progress?.let {
                ProgressDialog(it)
            }
        }
    }
}

@Composable
private fun sortingToOption(sorting: WordsSorting): Option<WordsSorting> {
    return when (sorting) {
        WordsSorting.ALPHABET -> Option(
            value = sorting,
            icon = OptionIcon(Icons.Default.ArrowUpward)
        )
        WordsSorting.ALPHABET_DESC -> Option(
            value = sorting,
            icon = OptionIcon(Icons.Default.ArrowDownward)
        )
        WordsSorting.NAME -> Option(
            value = sorting,
            icon = OptionIcon(
                vector = Icons.Default.Circle,
                tint = MaterialTheme.colorScheme.primary,
                size = 12.dp
            )
        )
        WordsSorting.LIKELY_CORRECT -> Option(
            value = sorting,
            icon = OptionIcon(
                vector = Icons.Default.Circle,
                tint = MaterialTheme.colorScheme.tertiary,
                size = 12.dp
            )
        )
        WordsSorting.LIKELY_INCORRECT -> Option(
            value = sorting,
            icon = OptionIcon(
                vector = Icons.Default.Circle,
                tint = MaterialTheme.colorScheme.error,
                size = 12.dp
            )
        )
        WordsSorting.NEEDS_REVIEW -> Option(
            value = sorting,
            icon = OptionIcon(
                vector = Icons.Default.Circle,
                tint = MaterialTheme.colorScheme.secondary,
                size = 12.dp
            )
        )
        WordsSorting.REVIEWED -> Option(
            value = sorting,
            icon = OptionIcon(Icons.Default.CheckCircle)
        )
    }
}

@Composable
private fun localizeSorting(sorting: WordsSorting): String {
    return when (sorting) {
        WordsSorting.ALPHABET -> stringResource(Res.string.sort_by_alphabet)
        WordsSorting.ALPHABET_DESC -> stringResource(Res.string.sort_by_alphabet_desc)
        WordsSorting.LIKELY_CORRECT -> stringResource(Res.string.likely_correct)
        WordsSorting.LIKELY_INCORRECT -> stringResource(Res.string.likely_incorrect)
        WordsSorting.NEEDS_REVIEW -> stringResource(Res.string.review_needed)
        WordsSorting.NAME -> stringResource(Res.string.names)
        WordsSorting.REVIEWED -> stringResource(Res.string.sort_by_reviewed)
    }
}

private fun filterSingletons(
    singletons: List<SingletonWord>,
    filter: WordsSorting
): List<SingletonWord> {
    return when (filter) {
        WordsSorting.ALPHABET -> singletons.sortedBy { it.word.lowercase() }
        WordsSorting.ALPHABET_DESC -> singletons.sortedByDescending { it.word.lowercase() }
        WordsSorting.LIKELY_CORRECT -> singletons.filter { it.result?.consensus == Consensus.LIKELY_CORRECT }
        WordsSorting.LIKELY_INCORRECT -> singletons.filter { it.result?.consensus == Consensus.LIKELY_INCORRECT }
        WordsSorting.NEEDS_REVIEW -> singletons.filter { it.result?.consensus == Consensus.NEEDS_REVIEW }
        WordsSorting.NAME -> singletons.filter { it.result?.consensus == Consensus.NAME }
        WordsSorting.REVIEWED -> singletons.filter { it.correct != null }
    }
}
