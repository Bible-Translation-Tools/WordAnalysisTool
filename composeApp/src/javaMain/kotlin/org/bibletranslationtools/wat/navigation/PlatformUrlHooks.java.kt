package org.bibletranslationtools.wat.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator

@Composable
actual fun BindNavigatorToPlatform(navigator: Navigator) {
    // No-op: There are no browser back/forward buttons on the JVM.
}

internal actual fun performPushState(path: String) { /* No-op */ }

internal actual fun performReplaceState(path: String) { /* No-op */ }

internal actual fun performBackNavigation() { /* No-op */ }

internal actual fun performReplaceAll(paths: List<String>) { /* No-op */ }