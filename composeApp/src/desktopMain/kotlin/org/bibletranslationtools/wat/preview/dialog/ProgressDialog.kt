package org.bibletranslationtools.wat.preview.dialog

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import org.bibletranslationtools.wat.ui.Progress
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Preview
@Composable
fun ProgressDialogPreview() {
    MainAppTheme {
        ProgressDialog(Progress(0.77f, "Processing. Please wait..."))
    }
}