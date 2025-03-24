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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.russhwolf.settings.ExperimentalSettingsApi
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.Model
import org.bibletranslationtools.wat.domain.ModelStatus
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.ui.control.MultiSelectList
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.color_scheme
import wordanalysistool.composeapp.generated.resources.models
import wordanalysistool.composeapp.generated.resources.select_models_limit
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

        var alert by remember { mutableStateOf<String?>(null) }

        val coroutineScope = rememberCoroutineScope()

        val modelsState = Model.entries.map {
            ModelStatus(
                it.value,
                rememberBooleanSetting(it.value, false)
            )
        }.toMutableStateList()
        val models = remember { modelsState }

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
                        Text(stringResource(Res.string.models))
                        Spacer(modifier = Modifier.width(16.dp))
                        MultiSelectList(
                            items = models,
                            selected = models.filter { it.active.value },
                            valueConverter = { it.model },
                            onSelect = { model ->
                                val activeModels = models.filter { it.active.value }
                                val status = !model.active.value

                                if (activeModels.size == 4 && status) {
                                    coroutineScope.launch {
                                        alert = getString(Res.string.select_models_limit)
                                    }
                                } else {
                                    model.active.value = status
                                }
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

            alert?.let {
                AlertDialog(
                    message = it,
                    onDismiss = { alert = null }
                )
            }
        }
    }
}