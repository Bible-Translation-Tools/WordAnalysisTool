package org.bibletranslationtools.wat.ui

import ComboBox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.ExperimentalSettingsApi
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.ClaudeAiModel
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.OpenAiModel
import org.bibletranslationtools.wat.domain.QwenModel
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.ui.control.AiDataView
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.claude_ai
import wordanalysistool.composeapp.generated.resources.claude_api_key_link
import wordanalysistool.composeapp.generated.resources.color_scheme
import wordanalysistool.composeapp.generated.resources.gemini
import wordanalysistool.composeapp.generated.resources.gemini_api_key_link
import wordanalysistool.composeapp.generated.resources.openai
import wordanalysistool.composeapp.generated.resources.openai_api_key_link
import wordanalysistool.composeapp.generated.resources.qwen
import wordanalysistool.composeapp.generated.resources.qwen_api_key_link
import wordanalysistool.composeapp.generated.resources.settings
import wordanalysistool.composeapp.generated.resources.system_language
import wordanalysistool.composeapp.generated.resources.theme_dark
import wordanalysistool.composeapp.generated.resources.theme_light
import wordanalysistool.composeapp.generated.resources.theme_system
import wordanalysistool.composeapp.generated.resources.use_apostrophe_regex

class SettingsScreen : Screen {

    @OptIn(ExperimentalSettingsApi::class)
    @Composable
    override fun Content() {
        val theme = rememberStringSetting(Settings.THEME.name, Theme.SYSTEM.name)
        val themeEnum = remember { derivedStateOf { Theme.valueOf(theme.value) } }

        val lightThemeStr = stringResource(Res.string.theme_light)
        val darkThemeStr = stringResource(Res.string.theme_dark)
        val systemThemeStr = stringResource(Res.string.theme_system)

        val locale = rememberStringSetting(Settings.LOCALE.name, Locales.EN.name)
        val localeEnum = remember { derivedStateOf { Locales.valueOf(locale.value) } }

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

        var apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        Scaffold(
            topBar = {
                TopNavigationBar(stringResource(Res.string.settings), isHome = false)
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.color_scheme))
                        Spacer(modifier = Modifier.width(16.dp))
                        ComboBox(
                            value = themeEnum.value,
                            options = Theme.entries,
                            onOptionSelected = { theme.value = it.name },
                            valueConverter = { value ->
                                when (value) {
                                    Theme.LIGHT -> lightThemeStr
                                    Theme.DARK -> darkThemeStr
                                    else -> systemThemeStr
                                }
                            },
                            modifier = Modifier.width(400.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.system_language))
                        Spacer(modifier = Modifier.width(16.dp))
                        ComboBox(
                            value = localeEnum.value,
                            options = Locales.entries,
                            onOptionSelected = { locale.value = it.name },
                            valueConverter = { value ->
                                when (value) {
                                    Locales.RU -> Locales.RU.value
                                    else -> Locales.EN.value
                                }
                            },
                            modifier = Modifier.width(400.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.gemini))
                        Spacer(modifier = Modifier.width(16.dp))
                        AiDataView(
                            model = geminiModel,
                            models = GeminiModel.entries.map { it.name },
                            aiApi = AiApi.GEMINI,
                            apiKey = geminiApiKey,
                            apiKeyLink = stringResource(Res.string.gemini_api_key_link),
                            isActive = geminiActive,
                            onModelChanged = { geminiModel = it },
                            onApiKeyChanged = { geminiApiKey = it },
                            onActiveChanged = { geminiActive = it }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.openai))
                        Spacer(modifier = Modifier.width(16.dp))
                        AiDataView(
                            model = openAiModel,
                            models = OpenAiModel.entries.map { it.name },
                            aiApi = AiApi.OPENAI,
                            apiKey = openAiApiKey,
                            apiKeyLink = stringResource(Res.string.openai_api_key_link),
                            isActive = openAiActive,
                            onModelChanged = { openAiModel = it },
                            onApiKeyChanged = { openAiApiKey = it },
                            onActiveChanged = { openAiActive = it }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.qwen))
                        Spacer(modifier = Modifier.width(16.dp))
                        AiDataView(
                            model = qwenModel,
                            models = QwenModel.entries.map { it.name },
                            aiApi = AiApi.QWEN,
                            apiKey = qwenApiKey,
                            apiKeyLink = stringResource(Res.string.qwen_api_key_link),
                            isActive = qwenActive,
                            onModelChanged = { qwenModel = it },
                            onApiKeyChanged = { qwenApiKey = it },
                            onActiveChanged = { qwenActive = it }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.claude_ai))
                        Spacer(modifier = Modifier.width(16.dp))
                        AiDataView(
                            model = claudeAiModel,
                            models = ClaudeAiModel.entries.map { it.name },
                            aiApi = AiApi.CLAUDE_AI,
                            apiKey = claudeAiApiKey,
                            apiKeyLink = stringResource(Res.string.claude_api_key_link),
                            isActive = claudeAiActive,
                            onModelChanged = { claudeAiModel = it },
                            onApiKeyChanged = { claudeAiApiKey = it },
                            onActiveChanged = { claudeAiActive = it }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.use_apostrophe_regex),
                            modifier = Modifier.width(200.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Checkbox(
                            checked = apostropheIsSeparator,
                            onCheckedChange = { apostropheIsSeparator = it }
                        )
                    }
                }
            }
        }
    }
}