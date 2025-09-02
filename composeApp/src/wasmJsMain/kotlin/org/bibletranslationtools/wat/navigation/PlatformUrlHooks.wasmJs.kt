package org.bibletranslationtools.wat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.browser.window
import org.bibletranslationtools.wat.ui.LoginScreen

@Composable
actual fun BindNavigatorToPlatform(navigator: Navigator) {
    LaunchedEffect(navigator) {
        window.onpopstate = {
            val newPath = window.location.pathname
            navigator.replaceAll(LoginScreen(newPath))
        }
    }
}

internal actual fun performPushState(path: String) {
    window.history.pushState(null, "", path)
}

internal actual fun performReplaceState(path: String) {
    window.history.replaceState(null, "", path)
}

internal actual fun performBackNavigation() {
    window.history.back()
}

internal actual fun performReplaceAll(paths: List<String>) {
    if (paths.isNotEmpty()) {
        window.history.replaceState(null, "", paths.first())
        paths.drop(1).forEach { p ->
            window.history.pushState(null, "", p)
        }
    }
}