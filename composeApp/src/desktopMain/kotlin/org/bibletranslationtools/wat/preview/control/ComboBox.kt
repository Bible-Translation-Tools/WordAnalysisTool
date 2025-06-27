package org.bibletranslationtools.wat.preview.control

import ComboBox
import Option
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Preview
@Composable
fun ComboBoxPreview() {
    MainAppTheme {
        ComboBox(
            value = "Option 2",
            options = listOf("Option 1", "Option 2", "Option 3").map(::Option),
            onOptionSelected = {},
            valueConverter = { it },
            label = "Select an option"
        )
    }
}