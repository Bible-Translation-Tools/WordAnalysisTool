package org.bibletranslationtools.wat.preview.control

import ComboBox
import Option
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.bibletranslationtools.wat.ui.theme.MainAppTheme

@Preview
@Composable
fun ComboBoxPreview() {
    MainAppTheme {
        ComboBox(
            value = "Option 2",
            options = listOf("Option 1", "Option 2", "Option 3").map(::Option),
            onOptionSelected = {},
            valueConverter = { it }
        )
    }
}