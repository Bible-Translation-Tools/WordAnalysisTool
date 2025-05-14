package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableComboBox(
    options: List<T> = emptyList(),
    onOptionSelected: (T) -> Unit = {},
    valueConverter: (T) -> String = { it.toString() },
    label: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isEnabled by rememberUpdatedState { options.isNotEmpty() }

    var searchTextState by remember { mutableStateOf(TextFieldValue("")) }
    var filteredOptions by remember { mutableStateOf(options) }

    LaunchedEffect(searchTextState) {
        filteredOptions = if (searchTextState.text.isBlank()) {
            options.take(100) // Show initial 100 if search is empty
        } else {
            options.filter {
                valueConverter(it)
                    .contains(searchTextState.text, ignoreCase = true)
            }.take(100) // Limit to 100 results for optimization
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredOptions.isNotEmpty(),
        onExpandedChange = {
            if (isEnabled()) {
                expanded = !expanded
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = searchTextState,
            enabled = isEnabled(),
            label = label?.let {{ Text(text = it) }},
            onValueChange = {
                searchTextState = it
                expanded = true
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            },
            modifier = modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded && filteredOptions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filteredOptions
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(valueConverter(option)) },
                        onClick = {
                            searchTextState = TextFieldValue(valueConverter(option))
                            expanded = false
                            onOptionSelected(option)
                        }
                    )
                }
        }
    }
}