package org.bibletranslationtools.wat.preview.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Preview
@Composable
fun ErrorDialogPreview() {
    MainAppTheme {
        AlertDialog(
            message = """
                Something went wrong
            """.trimIndent(),
            onDismiss = {}
        )
    }
}