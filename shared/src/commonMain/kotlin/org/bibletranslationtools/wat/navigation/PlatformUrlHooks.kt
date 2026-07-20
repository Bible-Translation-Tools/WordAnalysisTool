package org.bibletranslationtools.wat.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator

@Composable
expect fun BindNavigatorToPlatform(navigator: Navigator)

internal expect fun performPushState(path: String)
internal expect fun performReplaceState(path: String)
internal expect fun performBackNavigation()
internal expect fun performReplaceAll(paths: List<String>)