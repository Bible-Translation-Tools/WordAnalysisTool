package org.bibletranslationtools.wat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import dev.burnoo.compose.remembersetting.rememberStringSetting
import org.bibletranslationtools.wat.domain.Locales
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Theme
import org.bibletranslationtools.wat.platform.applyLocale
import org.bibletranslationtools.wat.ui.HomeScreen
import org.bibletranslationtools.wat.ui.theme.DarkColorScheme
import org.bibletranslationtools.wat.ui.theme.LightColorScheme
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Composable
fun App() {
    val theme by rememberStringSetting(Settings.THEME.name, Theme.SYSTEM.name)
    val colorScheme = when {
        theme == Theme.LIGHT.name -> LightColorScheme
        theme == Theme.DARK.name -> DarkColorScheme
        theme == Theme.SYSTEM.name && isSystemInDarkTheme() -> DarkColorScheme
        else -> LightColorScheme
    }

    val locale by rememberStringSetting(Settings.LOCALE.name, Locales.EN.name)
    applyLocale(locale.lowercase())

    MainAppTheme(colorScheme) {
        Navigator(HomeScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
