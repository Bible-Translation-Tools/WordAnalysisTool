package org.bibletranslationtools.wat.ui

import ComboBox
import Option
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.russhwolf.settings.ExperimentalSettingsApi
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSetting
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.Model
import org.bibletranslationtools.wat.domain.ModelStatus
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.ui.control.CustomTextButton
import org.bibletranslationtools.wat.ui.control.MultiSelectList
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.back
import wordanalysistool.shared.generated.resources.color_scheme
import wordanalysistool.shared.generated.resources.home
import wordanalysistool.shared.generated.resources.models
import wordanalysistool.shared.generated.resources.settings
import wordanalysistool.shared.generated.resources.sign_out
import wordanalysistool.shared.generated.resources.system_language
import wordanalysistool.shared.generated.resources.theme_dark
import wordanalysistool.shared.generated.resources.theme_light
import wordanalysistool.shared.generated.resources.theme_system

class SettingsScreen(private val user: User) : Screen {

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

        val navigator = LocalNavigator.currentOrThrow

        var alert by remember { mutableStateOf<String?>(null) }

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        val modelsState = Model.entries.map {
            ModelStatus(
                it.value,
                rememberBooleanSetting(it.value, false)
            )
        }.toMutableStateList()
        val models = remember { modelsState }

        var isModelsExpanded by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium
                            )
                            .weight(0.3f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            CustomTextButton(
                                onClick = navigator::pop,
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                text = stringResource(Res.string.back),
                                modifier = Modifier.align(Alignment.End)
                            )

                            Text(
                                text = stringResource(Res.string.settings),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.W500
                            )

                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(
                                    space = 4.dp,
                                    alignment = Alignment.Bottom
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                CustomTextButton(
                                    onClick = { UrlManager.replaceAll(HomeScreen(user)) },
                                    icon = Icons.Default.Home,
                                    text = stringResource(Res.string.home)
                                )
                                CustomTextButton(
                                    onClick = {
                                        accessToken = null
                                        UrlManager.replaceAll(LoginScreen())
                                    },
                                    icon = Icons.Default.Person,
                                    text = stringResource(
                                        Res.string.sign_out,
                                        user.username
                                    )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxHeight()
                            .padding(start = 48.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .width(800.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(Res.string.color_scheme))
                                ComboBox(
                                    value = themeEnum.value,
                                    options = Theme.entries.map(::Option),
                                    onOptionSelected = { theme.value = it.name },
                                    valueConverter = { value ->
                                        when (value) {
                                            Theme.LIGHT -> lightThemeStr
                                            Theme.DARK -> darkThemeStr
                                            else -> systemThemeStr
                                        }
                                    },
                                    modifier = Modifier.width(300.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(Res.string.system_language))
                                ComboBox(
                                    value = localeEnum.value,
                                    options = Locales.entries.map(::Option),
                                    onOptionSelected = { locale.value = it.name },
                                    valueConverter = { value ->
                                        when (value) {
                                            Locales.RU -> Locales.RU.value
                                            else -> Locales.EN.value
                                        }
                                    },
                                    modifier = Modifier.width(300.dp)
                                )
                            }

                            if (user.admin) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(end = 12.dp)
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                                onClick = { isModelsExpanded = !isModelsExpanded }
                                            )
                                    ) {
                                        Text(text = stringResource(Res.string.models))
                                        Icon(
                                            imageVector = if (isModelsExpanded) {
                                                Icons.Default.KeyboardArrowUp
                                            } else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null
                                        )
                                    }

                                    AnimatedVisibility(visible = isModelsExpanded) {
                                        MultiSelectList(
                                            items = models,
                                            selected = models.filter { it.active.value },
                                            valueConverter = { it.model },
                                            onSelect = { model ->
                                                model.active.value = !model.active.value
                                            },
                                            modifier = Modifier.padding(start = 16.dp)
                                        )
                                    }
                                }

                            }
                        }
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