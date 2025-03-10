package org.bibletranslationtools.wat.ui

import ComboBox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.ExperimentalSettingsApi
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.AiModel
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.OpenAiModel
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.ai_api
import wordanalysistool.composeapp.generated.resources.ai_api_key
import wordanalysistool.composeapp.generated.resources.ai_model
import wordanalysistool.composeapp.generated.resources.color_scheme
import wordanalysistool.composeapp.generated.resources.gemini
import wordanalysistool.composeapp.generated.resources.openai
import wordanalysistool.composeapp.generated.resources.settings
import wordanalysistool.composeapp.generated.resources.system_language
import wordanalysistool.composeapp.generated.resources.theme_dark
import wordanalysistool.composeapp.generated.resources.theme_light
import wordanalysistool.composeapp.generated.resources.theme_system

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
        var aiModels by remember { mutableStateOf<List<String>>(emptyList()) }

        var aiApiKey by rememberStringSetting(Settings.AI_API_KEY.name, "")
        var apiKeyVisible by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(aiApi.value) {
            aiModel = getModel(aiApiEnum.value, aiModel).name
            aiModels = when (aiApi.value) {
                AiApi.OPENAI.name -> OpenAiModel.entries.map { it.name }
                else -> GeminiModel.entries.map { it.name }
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
                        ComboBox(
                            value = themeEnum.value,
                            options = Theme.entries,
                            onOptionSelected = { theme.value = it.name },
                            valueConverter = { value ->
                                when (value) {
                                    Theme.LIGHT -> stringResource(Res.string.theme_light)
                                    Theme.DARK -> stringResource(Res.string.theme_dark)
                                    else -> stringResource(Res.string.theme_system)
                                }
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.system_language))
                        ComboBox(
                            value = localeEnum.value,
                            options = Locales.entries,
                            onOptionSelected = { locale.value = it.name },
                            valueConverter = { value ->
                                when (value) {
                                    Locales.RU -> Locales.RU.value
                                    else -> Locales.EN.value
                                }
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.ai_api))
                        ComboBox(
                            value = aiApiEnum.value,
                            options = AiApi.entries,
                            onOptionSelected = { aiApi.value = it.name },
                            valueConverter = { value ->
                                when (value) {
                                    AiApi.OPENAI -> stringResource(Res.string.openai)
                                    else -> stringResource(Res.string.gemini)
                                }
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.ai_model))
                        ComboBox(
                            value = aiModel,
                            options = aiModels,
                            onOptionSelected = { aiModel = it },
                            valueConverter = { value ->
                                getModel(aiApiEnum.value, value).value
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.width(700.dp)
                    ) {
                        Text(stringResource(Res.string.ai_api_key))
                        TextField(
                            value = aiApiKey,
                            onValueChange = { aiApiKey = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    private fun <A> getModel(api: A, model: String): AiModel {
        return when (api) {
            AiApi.OPENAI -> OpenAiModel.getOrDefault(model)
            else -> GeminiModel.getOrDefault(model)
        }
    }
}