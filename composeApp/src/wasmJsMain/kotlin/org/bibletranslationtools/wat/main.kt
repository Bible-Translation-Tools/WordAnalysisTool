package org.bibletranslationtools.wat

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.bibletranslationtools.wat.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    document.body?.let {
        ComposeViewport(it) {
            App()
        }
    }
}