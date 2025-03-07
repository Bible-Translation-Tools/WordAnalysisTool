package org.bibletranslationtools.wat

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import org.bibletranslationtools.wat.ui.HomeScreen
import org.bibletranslationtools.wat.ui.theme.DarkColors
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Composable
fun App() {
    MainAppTheme(themeColors = DarkColors) {
        Navigator(HomeScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}