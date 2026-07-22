package org.bibletranslationtools.wat.preview.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Preview
@Composable
fun ProgressDialogPreview() {
    MainAppTheme {
        ProgressDialog(Progress(0.77f, "Processing. Please wait..."))
    }
}