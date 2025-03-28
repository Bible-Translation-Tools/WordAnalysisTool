package org.bibletranslationtools.wat.ui

import ComboBox
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.SingletonWord
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.Model
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.ui.control.ExtraAction
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.default_prompt
import wordanalysistool.composeapp.generated.resources.refresh_batch
import wordanalysistool.composeapp.generated.resources.save_report
import wordanalysistool.composeapp.generated.resources.total_misspellings
import wordanalysistool.composeapp.generated.resources.total_singletons
import wordanalysistool.composeapp.generated.resources.total_undefined

class AnalyzeScreen(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: List<Verse>
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<AnalyzeViewModel> {
            parametersOf(language, verses)
        }

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AnalyzeEvent.Idle)
        var selectedWord by remember { mutableStateOf<Pair<String, SingletonWord>?>(null) }

        val modelsState = Model.entries.mapNotNull {
            val active = rememberBooleanSetting(it.value, false).value
            if (active) it.value else null
        }.toMutableStateList()
        val models = remember { modelsState }

        var batchId by rememberStringSettingOrNull("batchId-${language.ietfCode}-${resourceType}")

        val prompt by rememberStringSetting(
            Settings.PROMPT.name,
            stringResource(Res.string.default_prompt)
        )

        val apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        var sortWords by rememberStringSetting(
            Settings.SORT_WORDS.name,
            SortWords.BY_ALPHABET.name
        )

        val wordsListState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(event) {
            when (event) {
                is AnalyzeEvent.BatchCreated -> {
                    batchId = (event as AnalyzeEvent.BatchCreated).value
                }
                is AnalyzeEvent.ReadyToCreateBatch -> {
                    viewModel.onEvent(AnalyzeEvent.CreateBatch)
                }
                else -> Unit
            }
        }

        LaunchedEffect(batchId, models) {
            batchId?.let {
                viewModel.onEvent(AnalyzeEvent.UpdateBatchId(it))
                viewModel.onEvent(AnalyzeEvent.FetchBatch(it))
            }
            if (models.isNotEmpty()) {
                viewModel.onEvent(AnalyzeEvent.UpdateModels(models))
            }
        }

        LaunchedEffect(prompt) {
            viewModel.onEvent(AnalyzeEvent.UpdatePrompt(prompt))
        }

        LaunchedEffect(apostropheIsSeparator) {
            viewModel.onEvent(AnalyzeEvent.FindSingletons(apostropheIsSeparator))
        }

        LaunchedEffect(sortWords) {
            viewModel.onEvent(AnalyzeEvent.SortedWords(
                SortWords.valueOf(sortWords)
            ))
        }

        Scaffold(
            topBar = {
                TopNavigationBar(
                    title = "[${language.ietfCode}] ${language.name} - $resourceType",
                    isHome = false,
                    ExtraAction(
                        title = stringResource(Res.string.refresh_batch),
                        icon = Icons.Default.Refresh,
                        onClick = {
                            batchId = null
                            viewModel.onEvent(AnalyzeEvent.CreateBatch)
                        }
                    ),
                    ExtraAction(
                        title = stringResource(Res.string.save_report),
                        icon = Icons.Default.Save,
                        onClick = {
                            viewModel.onEvent(AnalyzeEvent.SaveReport)
                        }
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    state.batchProgress > 0f -> {
                        LinearProgressIndicator(
                            progress = { state.batchProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    state.batchProgress == 0f -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (state.batchProgress >= 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.width(70.dp)
                                .height(30.dp)
                                .offset(y = 5.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "${(state.batchProgress * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(0.3f)) {
                        Column(
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    Res.string.total_singletons,
                                    state.singletons.size
                                ),
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(
                                    Res.string.total_misspellings,
                                    state.singletons.filter {
                                        it.value.result?.consensus == Consensus.MISSPELLING
                                    }.size
                                ),
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(
                                    Res.string.total_undefined,
                                    state.singletons.filter {
                                        it.value.result?.consensus == Consensus.UNDEFINED
                                    }.size
                                ),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ComboBox(
                                value = SortWords.valueOf(sortWords),
                                options = SortWords.entries,
                                onOptionSelected = { sort ->
                                    sortWords = sort.name
                                    scope.launch {
                                        delay(100)
                                        wordsListState.animateScrollToItem(0)
                                    }
                                },
                                valueConverter = { sort ->
                                    sort.value
                                },
                                label = "Sort words"
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn(state = wordsListState) {
                            state.singletons.forEach { (word, singleton) ->
                                item(key = word) {
                                    Text(
                                        text = word,
                                        fontWeight = if (selectedWord?.first == word)
                                            FontWeight.Bold else FontWeight.Normal,
                                        color = when (singleton.result?.consensus) {
                                            Consensus.MISSPELLING -> MaterialTheme.colorScheme.error
                                            Consensus.PROPER_NAME -> MaterialTheme.colorScheme.tertiary
                                            Consensus.SOMETHING_ELSE -> MaterialTheme.colorScheme.tertiary
                                            Consensus.UNDEFINED -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.onBackground
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedWord = word to singleton
                                            }
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider()

                    Column(modifier = Modifier.weight(0.7f)) {
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.weight(0.6f)
                                .padding(bottom = 10.dp, start = 20.dp)
                        ) {
                            selectedWord?.let { (word, singleton) ->
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = word,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    val annotatedText = buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            append("${singleton.ref.bookName} ")
                                            append("(${singleton.ref.bookSlug.uppercase()}) ")
                                            append("${singleton.ref.chapter}:${singleton.ref.number} ")
                                        }
                                        append(singleton.ref.text)
                                    }
                                    Text(annotatedText)
                                }
                            }
                        }

                        HorizontalDivider()

                        Box(modifier = Modifier.padding(start = 20.dp, top = 20.dp).weight(0.4f)) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.verticalScroll(rememberScrollState())
                                    .fillMaxWidth()
                            ) {

                            }
                        }
                    }
                }
            }

            state.alert?.let {
                AlertDialog(
                    message = it,
                    onDismiss = { viewModel.onEvent(AnalyzeEvent.ClearAlert) }
                )
            }

            state.progress?.let {
                ProgressDialog(it)
            }
        }
    }
}
