package org.bibletranslationtools.wat

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bibletranslationtools.wat.di.initKoin
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.app_name
import java.awt.Dimension

/** The review layout stops making sense below this. */
private val MIN_WINDOW_SIZE = DpSize(800.dp, 600.dp)
private val INITIAL_WINDOW_SIZE = DpSize(1280.dp, 900.dp)

fun main() {
    initKoin()
    application {
        val windowState = rememberWindowState(size = INITIAL_WINDOW_SIZE)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = stringResource(Res.string.app_name),
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(
                    MIN_WINDOW_SIZE.width.value.toInt(),
                    MIN_WINDOW_SIZE.height.value.toInt()
                )
            }

            App()
        }
    }
}
