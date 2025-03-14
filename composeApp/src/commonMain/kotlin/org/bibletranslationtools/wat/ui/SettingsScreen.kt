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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.ExperimentalSettingsApi
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.AiModel
import org.bibletranslationtools.wat.domain.ClaudeAiModel
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.OpenAiModel
import org.bibletranslationtools.wat.domain.QwenModel
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.ai_api
import wordanalysistool.composeapp.generated.resources.ai_api_key
import wordanalysistool.composeapp.generated.resources.ai_model
import wordanalysistool.composeapp.generated.resources.claude_ai
import wordanalysistool.composeapp.generated.resources.claude_api_key_link
import wordanalysistool.composeapp.generated.resources.color_scheme
import wordanalysistool.composeapp.generated.resources.don_t_have_key
import wordanalysistool.composeapp.generated.resources.gemini
import wordanalysistool.composeapp.generated.resources.gemini_api_key_link
import wordanalysistool.composeapp.generated.resources.get_one_here
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

        val locale = rememberStringSetting(Settings.LOCALE.name, Locales.EN.name)
        val localeEnum = remember { derivedStateOf { Locales.valueOf(locale.value) } }

        val aiApi = rememberStringSetting(Settings.AI_API.name, AiApi.GEMINI.name)
        val aiApiEnum = remember { derivedStateOf { AiApi.valueOf(aiApi.value) } }

        var aiModel by rememberStringSetting(Settings.AI_MODEL.name, GeminiModel.FLASH_2.name)
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

        var aiModels by remember { mutableStateOf<List<String>>(emptyList()) }

        var aiApiKey by rememberStringSetting(Settings.AI_API_KEY.name, "")
        var geminiApiKey by rememberStringSetting(Settings.GEMINI_API_KEY.name, "")
        var openAiApiKey by rememberStringSetting(Settings.OPENAI_API_KEY.name, "")
        var qwenApiKey by rememberStringSetting(Settings.QWEN_API_KEY.name, "")
        var claudeAiApiKey by rememberStringSetting(Settings.CLAUDEAI_API_KEY.name, "")

        var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
        var apiKeyLink by rememberSaveable { mutableStateOf("") }

        var apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        val lightThemeStr = stringResource(Res.string.theme_light)
        val darkThemeStr = stringResource(Res.string.theme_dark)
        val systemThemeStr = stringResource(Res.string.theme_system)
        val openAiStr = stringResource(Res.string.openai)
        val geminiStr = stringResource(Res.string.gemini)
        val qwenAiStr = stringResource(Res.string.qwen)
        val claudeAiStr = stringResource(Res.string.claude_ai)

        LaunchedEffect(aiApiEnum.value) {
            when (aiApiEnum.value) {
                AiApi.OPENAI -> {
                    aiModel = openAiModel
                    aiModels = OpenAiModel.entries.map { it.name }
                    aiApiKey = openAiApiKey
                    apiKeyLink = getString(Res.string.openai_api_key_link)
                }
                AiApi.QWEN -> {
                    aiModel = qwenModel
                    aiModels = QwenModel.entries.map { it.name }
                    aiApiKey = qwenApiKey
                    apiKeyLink = getString(Res.string.qwen_api_key_link)
                }
                AiApi.CLAUDE_AI -> {
                    aiModel = claudeAiModel
                    aiModels = ClaudeAiModel.entries.map { it.name }
                    aiApiKey = claudeAiApiKey
                    apiKeyLink = getString(Res.string.claude_api_key_link)
                }
                else -> {
                    aiModel = geminiModel
                    aiModels = GeminiModel.entries.map { it.name }
                    aiApiKey = geminiApiKey
                    apiKeyLink = getString(Res.string.gemini_api_key_link)
                }
            }
        }

        LaunchedEffect(aiModel) {
            when (aiApiEnum.value) {
                AiApi.OPENAI -> openAiModel = aiModel
                AiApi.QWEN -> qwenModel = aiModel
                AiApi.CLAUDE_AI -> claudeAiModel = aiModel
                else -> geminiModel = aiModel
            }
        }

        LaunchedEffect(aiApiKey) {
            when (aiApiEnum.value) {
                AiApi.OPENAI -> openAiApiKey = aiApiKey
                AiApi.QWEN -> qwenApiKey = aiApiKey
                AiApi.CLAUDE_AI -> claudeAiApiKey = aiApiKey
                else -> geminiApiKey = aiApiKey
            }
        }

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
                        Text(stringResource(Res.string.ai_api))
                        Spacer(modifier = Modifier.width(16.dp))
                        ComboBox(
                            value = aiApiEnum.value,
                            options = AiApi.entries,
                            onOptionSelected = { aiApi.value = it.name },
                            valueConverter = { value ->
                                when (value) {
                                    AiApi.OPENAI -> openAiStr
                                    AiApi.QWEN -> qwenAiStr
                                    AiApi.CLAUDE_AI -> claudeAiStr
                                    else -> geminiStr
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
                        Text(stringResource(Res.string.ai_model))
                        Spacer(modifier = Modifier.width(16.dp))
                        ComboBox(
                            value = aiModel,
                            options = aiModels,
                            onOptionSelected = { aiModel = it },
                            valueConverter = { value ->
                                getModel(aiApiEnum.value, value).value
                            },
                            modifier = Modifier.width(400.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.ai_api_key))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = buildAnnotatedString {
                                    append(stringResource(Res.string.don_t_have_key))
                                    append(" ")

                                    withLink(
                                        LinkAnnotation.Url(
                                            url = apiKeyLink,
                                            styles = TextLinkStyles(
                                                style = SpanStyle(color = MaterialTheme.colorScheme.secondary)
                                            )
                                        )
                                    ) {
                                        append(stringResource(Res.string.get_one_here))
                                    }
                                }
                            )
                            TextField(
                                value = aiApiKey,
                                onValueChange = {
                                    aiApiKey = it
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password
                                ),
                                visualTransformation = if (apiKeyVisible)
                                    VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    val image = if (apiKeyVisible)
                                        Icons.Filled.Visibility
                                    else Icons.Filled.VisibilityOff

                                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible}){
                                        Icon(imageVector  = image, null)
                                    }
                                },
                                maxLines = 3,
                                modifier = Modifier.width(400.dp)
                            )
                        }
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

    private fun <A> getModel(api: A, model: String): AiModel {
        return when (api) {
            AiApi.OPENAI -> OpenAiModel.getOrDefault(model)
            AiApi.QWEN -> QwenModel.getOrDefault(model)
            AiApi.CLAUDE_AI -> ClaudeAiModel.getOrDefault(model)
            else -> GeminiModel.getOrDefault(model)
        }
    }
}