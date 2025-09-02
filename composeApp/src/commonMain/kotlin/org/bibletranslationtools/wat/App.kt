package org.bibletranslationtools.wat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.platform.applyLocale
import org.bibletranslationtools.wat.ui.LoginScreen
import org.bibletranslationtools.wat.ui.theme.DarkColorScheme
import org.bibletranslationtools.wat.ui.theme.LightColorScheme
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Composable
fun App(initialPath: String? = null) {
    val theme by rememberStringSetting(Settings.THEME.name, Theme.SYSTEM.name)
    val colorScheme = when {
        theme == Theme.LIGHT.name -> LightColorScheme
        theme == Theme.DARK.name -> DarkColorScheme
        theme == Theme.SYSTEM.name && isSystemInDarkTheme() -> DarkColorScheme
        else -> LightColorScheme
    }

    val locale by rememberStringSetting(Settings.LOCALE.name, Locales.EN.name)
    applyLocale(locale.lowercase())

    val initialScreen = remember(initialPath) {
        LoginScreen(initialPath)
    }

    MainAppTheme(colorScheme) {
        Navigator(initialScreen) { navigator ->
            UrlManager.Init(navigator)
            SlideTransition(navigator)
        }
    }
}
