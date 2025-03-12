package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.ClaudeAiModel
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.OpenAiModel
import org.bibletranslationtools.wat.domain.QwenModel
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.ErrorDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.dialogs.PromptEditorDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.ask_ai
import wordanalysistool.composeapp.generated.resources.claude_ai
import wordanalysistool.composeapp.generated.resources.consensus
import wordanalysistool.composeapp.generated.resources.edit_prompt
import wordanalysistool.composeapp.generated.resources.gemini
import wordanalysistool.composeapp.generated.resources.openai
import wordanalysistool.composeapp.generated.resources.qwen

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

        val words by viewModel.singletonWords.collectAsStateWithLifecycle()
        var refs by remember { mutableStateOf<List<Verse>>(emptyList()) }
        var selectedWord by remember { mutableStateOf<String?>(null) }

        var geminiModel by rememberStringSetting(
            Settings.GEMINI_MODEL.name,
            GeminiModel.FLASH_2.name
        )
        var openAiModel by rememberStringSetting(
            Settings.OPENAI_MODEL.name,
            OpenAiModel.GPT_3_5_TURBO.name
        )
        var qwenModel by rememberStringSetting(
            Settings.QWEN_MODEL.name,
            QwenModel.QWEN_PLUS.name
        )
        var claudeAiModel by rememberStringSetting(
            Settings.CLAUDEAI_MODEL.name,
            ClaudeAiModel.CLAUDE_3_7_SONNET.name
        )

        var geminiApiKey by rememberStringSetting(Settings.GEMINI_API_KEY.name, "")
        var openAiApiKey by rememberStringSetting(Settings.OPENAI_API_KEY.name, "")
        var qwenApiKey by rememberStringSetting(Settings.QWEN_API_KEY.name, "")
        var claudeAiApiKey by rememberStringSetting(Settings.CLAUDEAI_API_KEY.name, "")

        var geminiActive by rememberBooleanSetting(Settings.GEMINI_ACTIVE.name, false)
        var openAiActive by rememberBooleanSetting(Settings.OPENAI_ACTIVE.name, false)
        var qwenActive by rememberBooleanSetting(Settings.QWEN_ACTIVE.name, false)
        var claudeAiActive by rememberBooleanSetting(Settings.CLAUDEAI_ACTIVE.name, false)

        val apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        var promptEditorShown by remember { mutableStateOf(false) }

        LaunchedEffect(geminiModel, geminiApiKey, geminiActive) {
            viewModel.setupGemini(geminiModel, geminiApiKey, geminiActive)
        }

        LaunchedEffect(openAiModel, openAiApiKey, openAiActive) {
            viewModel.setupOpenAi(openAiModel, openAiApiKey, openAiActive)
        }

        LaunchedEffect(qwenModel, qwenApiKey, qwenActive) {
            viewModel.setupQwen(qwenModel, qwenApiKey, qwenActive)
        }

        LaunchedEffect(claudeAiModel, claudeAiApiKey, claudeAiActive) {
            viewModel.setupClaudeAi(claudeAiModel, claudeAiApiKey, claudeAiActive)
        }

        LaunchedEffect(selectedWord) {
            if (selectedWord == null) {
                viewModel.clearAiResponses()
            }
        }

        LaunchedEffect(apostropheIsSeparator) {
            viewModel.updateApostropheIsSeparator(apostropheIsSeparator)
        }

        Scaffold(
            topBar = {
                TopNavigationBar(
                    title = "[${language.ietfCode}] ${language.name} - $resourceType",
                    isHome = false
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    LazyColumn(modifier = Modifier.weight(0.3f)) {
                        words.forEach { word ->
                            item {
                                Text(
                                    text = word.key,
                                    fontWeight = if (selectedWord == word.key)
                                        FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            refs = word.value.refs
                                            selectedWord = word.key
                                            viewModel.updatePrompt(word.key, refs.first())
                                            viewModel.clearAiResponses()
                                        }
                                )
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
                            if (refs.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    selectedWord?.let {
                                        Text(
                                            text = it,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    refs.first().let { ref ->
                                        val annotatedText = buildAnnotatedString {
                                            withStyle(
                                                style = SpanStyle(
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                append("${ref.bookName} ")
                                                append("(${ref.bookSlug.uppercase()}) ")
                                                append("${ref.chapter}:${ref.number} ")
                                            }
                                            append(ref.text)
                                        }
                                        Text(annotatedText)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(onClick = viewModel::askAi) {
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
                                viewModel.aiResponses.forEach { (api, response) ->
                                    Text(text = buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append("${getAiName(api)}: ")
                                        }
                                        append(response)
                                    })
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                viewModel.consensus?.let {
                                    Text(text = buildAnnotatedString {
                                        withStyle(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            append("${stringResource(Res.string.consensus)}: ")
                                        }
                                        append(it)
                                    })
                                }
                            }
                        }
                    }
                }
            }

            viewModel.error?.let {
                ErrorDialog(error = it, onDismiss = { viewModel.clearError() })
            }

            viewModel.progress?.let {
                ProgressDialog(it)
            }

            if (promptEditorShown) {
                PromptEditorDialog(
                    prompt = viewModel.prompt,
                    onDismiss = { promptEditorShown = false },
                    onConfirm = { viewModel.updatePrompt(it) }
                )
            }
        }
    }
}

@Composable
private fun getAiName(ai: AiApi): String {
    return when (ai) {
        AiApi.GEMINI -> stringResource(Res.string.gemini)
        AiApi.OPENAI -> stringResource(Res.string.openai)
        AiApi.QWEN -> stringResource(Res.string.qwen)
        AiApi.CLAUDE_AI -> stringResource(Res.string.claude_ai)
    }
}