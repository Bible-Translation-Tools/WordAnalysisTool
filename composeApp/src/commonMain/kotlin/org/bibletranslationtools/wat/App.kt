package org.bibletranslationtools.wat

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.bibletranslationtools.wat.ui.HomeScreen
import org.bibletranslationtools.wat.ui.theme.DarkColorScheme
import org.bibletranslationtools.wat.ui.theme.LightColorScheme
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Composable
fun App() {
    MainAppTheme(themeColorScheme = DarkColorScheme) {
        Navigator(HomeScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}