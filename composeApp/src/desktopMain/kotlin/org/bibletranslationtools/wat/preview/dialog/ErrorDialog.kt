package org.bibletranslationtools.wat.preview.dialog

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import org.bibletranslationtools.wat.ui.dialogs.ErrorDialog
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Preview
@Composable
fun ErrorDialogPreview() {
    MainAppTheme {
        ErrorDialog(
            error = """
                Something went wrong
            """.trimIndent(),
            onDismiss = {}
        )
    }
}