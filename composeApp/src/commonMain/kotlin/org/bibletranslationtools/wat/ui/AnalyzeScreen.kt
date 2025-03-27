package org.bibletranslationtools.wat.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
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
import org.bibletranslationtools.wat.ui.dialogs.PromptEditorDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.ask_ai
import wordanalysistool.composeapp.generated.resources.consensus
import wordanalysistool.composeapp.generated.resources.did_not_respond
import wordanalysistool.composeapp.generated.resources.edit_prompt
import wordanalysistool.composeapp.generated.resources.refresh_batch
import wordanalysistool.composeapp.generated.resources.save_report

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

        val apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        var promptEditorShown by remember { mutableStateOf(false) }

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

        LaunchedEffect(selectedWord) {
            if (selectedWord == null) {
                viewModel.onEvent(AnalyzeEvent.ClearResponse)
            }
        }

        LaunchedEffect(apostropheIsSeparator) {
            viewModel.onEvent(AnalyzeEvent.FindSingletons(apostropheIsSeparator))
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
                    LazyColumn(modifier = Modifier.weight(0.3f)) {
                        items(
                            items = state.singletons.toList(),
                            key = { it.first }
                        ) { (word, singleton) ->
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
                                        viewModel.onEvent(
                                            AnalyzeEvent.UpdatePrompt.FromVerse(
                                                word,
                                                singleton.ref
                                            )
                                        )
                                        viewModel.onEvent(AnalyzeEvent.ClearResponse)
                                    }
                            )
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.onEvent(AnalyzeEvent.Chat(word))
                                        }
                                    ) {
                                        Text(text = stringResource(Res.string.ask_ai))
                                    }
                                    IconButton(
                                        onClick = { promptEditorShown = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = stringResource(
                                                Res.string.edit_prompt
                                            )
                                        )
                                    }
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
                                state.aiResponses.forEach { (model, response) ->
                                    Text(text = buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append("$model: ")
                                        }
                                        append(
                                            response ?: stringResource(Res.string.did_not_respond)
                                        )
                                    })
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                state.consensus?.let {
                                    Text(text = buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            append("${stringResource(Res.string.consensus)}: ")
                                        }
                                        append(it.consensus.name)
                                    })
                                }
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

            if (promptEditorShown) {
                PromptEditorDialog(
                    prompt = state.prompt!!,
                    onDismiss = { promptEditorShown = false },
                    onConfirm = { viewModel.onEvent(AnalyzeEvent.UpdatePrompt.FromString(it)) }
                )
            }
        }
    }
}
