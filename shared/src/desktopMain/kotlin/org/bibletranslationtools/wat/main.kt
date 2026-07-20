package org.bibletranslationtools.wat

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.bibletranslationtools.wat.di.initKoin
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.app_name

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
        ) {
            App()
        }
    }
}