package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.ErrorDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.koin.core.parameter.parametersOf

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

        LaunchedEffect(aiApi, aiModel, aiApiKey) {
            viewModel.setupModel(aiApi, aiModel, aiApiKey)
        }

        LaunchedEffect(selectedWord) {
            if (selectedWord == null) {
                viewModel.clearAiResponse()
            }
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
                        .padding(20.dp)
                ) {
                    LazyColumn(modifier = Modifier.weight(0.2f)) {
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
                                            viewModel.clearAiResponse()
                                        }
                                )
                            }
                        }
                    }

                    VerticalDivider()

                    Column(modifier = Modifier.weight(0.8f)) {
                        Column(
                            modifier = Modifier.weight(0.5f)
                                .padding(horizontal = 20.dp)
                        ) {
                            selectedWord?.let {
                                Text(
                                    text = it,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                )
                            }
                            refs.forEach { ref ->
                                val verse = "[${ref.bookSlug}] ${ref.bookName} " +
                                        "${ref.chapter}:${ref.number} ${ref.text}"
                                Text(
                                    text = verse,
                                    modifier = Modifier
                                        .clickable {
                                            selectedWord?.let {
                                                viewModel.askAi(it, ref)
                                            }
                                        }
                                )
                            }
                        }

                        HorizontalDivider()

                        Box(modifier = Modifier.padding(20.dp).weight(0.5f)) {
                            Text(
                                text = viewModel.aiResponse ?: "",
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
        }
    }
}