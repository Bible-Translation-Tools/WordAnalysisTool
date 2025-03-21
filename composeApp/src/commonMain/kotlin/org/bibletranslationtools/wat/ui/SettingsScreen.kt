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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.ExperimentalSettingsApi
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.Model
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.ui.control.MultiSelectList
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.color_scheme
import wordanalysistool.composeapp.generated.resources.models
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

        var model1 by rememberStringSettingOrNull(Settings.MODEL_1.name)
        var model2 by rememberStringSettingOrNull(Settings.MODEL_2.name)
        var model3 by rememberStringSettingOrNull(Settings.MODEL_3.name)
        var model4 by rememberStringSettingOrNull(Settings.MODEL_4.name)

        var modelsMap by remember { mutableStateOf<Map<String, Model?>>(mapOf(
            Settings.MODEL_1.name to model1?.let { Model.ofValue(it) },
            Settings.MODEL_2.name to model2?.let { Model.ofValue(it) },
            Settings.MODEL_3.name to model3?.let { Model.ofValue(it) },
            Settings.MODEL_4.name to model4?.let { Model.ofValue(it) },
        )) }
        var models by remember { mutableStateOf<List<Model>>(modelsMap.mapNotNull { it.value })}

        var apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        LaunchedEffect(modelsMap) {
            model1 = modelsMap[Settings.MODEL_1.name]?.value
            model2 = modelsMap[Settings.MODEL_2.name]?.value
            model3 = modelsMap[Settings.MODEL_3.name]?.value
            model4 = modelsMap[Settings.MODEL_4.name]?.value
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
                        Text(stringResource(Res.string.models))
                        Spacer(modifier = Modifier.width(16.dp))
                        MultiSelectList(
                            items = Model.entries,
                            selected = models,
                            valueConverter = { it.value },
                            onSelect = { model ->
                                models = if (model in models) {
                                    models.filter { it != model }
                                } else {
                                    models + model
                                }

                                modelsMap = mapOf(
                                    Settings.MODEL_1.name to models.getOrNull(0),
                                    Settings.MODEL_2.name to models.getOrNull(1),
                                    Settings.MODEL_3.name to models.getOrNull(2),
                                    Settings.MODEL_4.name to models.getOrNull(3)
                                )
                            }
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