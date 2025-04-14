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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.delete_batch
import wordanalysistool.composeapp.generated.resources.logout
import wordanalysistool.composeapp.generated.resources.process_words
import wordanalysistool.composeapp.generated.resources.save_report
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

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        val wordsListState = rememberLazyListState()
        val statuses = remember { mutableStateListOf<String>() }

        var showStatuses by remember { mutableStateOf(false) }

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

        LaunchedEffect(wordsSorting) {
            val sorting = try {
                WordsSorting.valueOf(wordsSorting)
            } catch (_: Exception) {
                WordsSorting.ALPHABET
            }
            viewModel.onEvent(AnalyzeEvent.UpdateSorting(sorting))
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

        Scaffold(
            topBar = {
                TopNavigationBar(
                    title = "[${language.ietfCode}] ${language.name} - $resourceType",
                    user = user,
                    page = PageType.ANALYZE,
                    ExtraAction(
                        title = stringResource(Res.string.delete_batch),
                        icon = Icons.Default.Refresh,
                        onClick = {
                            viewModel.onEvent(AnalyzeEvent.DeleteBatch)
                        }
                    ),
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
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (showStatuses) {
                    StatusBox(
                        statuses = statuses,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 4.dp)
                            .weight(1f)
                    ) {
                        Column(modifier = Modifier.weight(0.3f)) {
                            Column(
                                modifier = Modifier.padding(end = 8.dp, top = 8.dp)
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
                                Button(
                                    onClick = { viewModel.onEvent(AnalyzeEvent.BatchWords) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(Res.string.process_words))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                ComboBox(
                                    value = sorting,
                                    options = WordsSorting.entries.map { sortingToOption(it) },
                                    onOptionSelected = { sort: WordsSorting ->
                                        wordsSorting = sort.name
                                    },
                                    valueConverter = { sort ->
                                        sort.value
                                    },
                                    label = stringResource(Res.string.sort_words)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyColumn(
                                state = wordsListState,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                items(items = state.singletons, key = { it.word }) { singleton ->
                                    SingletonRow(
                                        singleton = singleton,
                                        selected = selectedWord == singleton,
                                        direction = language.direction,
                                        onSelect = { selectedWord = singleton }
                                    )
                                }
                            }
                        }

                        VerticalDivider()

                        SingletonCard(
                            word = selectedWord,
                            onAnswer = {
                                viewModel.onEvent(
                                    AnalyzeEvent.UpdateCorrect(selectedWord!!.word, it)
                                )
                            },
                            modifier = Modifier.weight(0.7f)
                        )
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
