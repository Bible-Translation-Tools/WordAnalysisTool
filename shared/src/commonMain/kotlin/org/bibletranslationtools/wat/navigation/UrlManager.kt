package org.bibletranslationtools.wat.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import org.bibletranslationtools.wat.ui.ReviewScreen

object UrlManager {
    private var navigator: Navigator? = null

    @Composable
    fun Init(navigator: Navigator) {
        this.navigator = navigator
        BindNavigatorToPlatform(navigator)
    }

    fun push(screen: Screen) {
        navigator?.push(screen)
        performPushState(screenToPath(screen))
    }

    fun replaceAll(screen: Screen) {
        navigator?.replaceAll(screen)
        performReplaceState(screenToPath(screen))
    }

    fun replaceAll(screens: List<Screen>) {
        if (screens.isEmpty()) return
        navigator?.replaceAll(screens)
        performReplaceAll(screens.map { screenToPath(it) })
    }

    fun pop() {
        navigator?.pop()
        performBackNavigation()
    }

    private fun screenToPath(screen: Screen): String {
        return when (screen) {
            is ReviewScreen -> "/${screen.ietfCode}_${screen.resourceType}"
            else -> "/"
        }
    }
}