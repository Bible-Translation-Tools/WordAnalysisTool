package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mikepenz.markdown.m3.Markdown
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.ErrorDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.dialogs.PromptEditorDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.ask_ai
import wordanalysistool.composeapp.generated.resources.edit_prompt

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

        val aiApi by rememberStringSetting(Settings.AI_API.name, AiApi.GEMINI.name)
        val aiModel by rememberStringSetting(Settings.AI_MODEL.name, GeminiModel.FLASH_2.name)
        val aiApiKey by rememberStringSetting(Settings.AI_API_KEY.name, "")
        val apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        var promptEditorShown by remember { mutableStateOf(false) }

        LaunchedEffect(aiApi, aiModel, aiApiKey) {
            viewModel.setupModel(aiApi, aiModel, aiApiKey)
        }

        LaunchedEffect(selectedWord) {
            if (selectedWord == null) {
                viewModel.clearAiResponse()
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
                                            viewModel.clearAiResponse()
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
                            Markdown(
                                content = viewModel.aiResponse ?: "",
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
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