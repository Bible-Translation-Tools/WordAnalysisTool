package org.bibletranslationtools.wat.ui.control

import TrailingIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A select that takes several options at once: the field lists what is chosen,
 * and the menu stays open while options are ticked off, like a multiple select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectComboBox(
    options: List<T>,
    selected: List<T>,
    onToggle: (T) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    valueConverter: (T) -> String = { it?.toString() ?: "" },
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options.isNotEmpty()) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.joinToString(", ", transform = valueConverter),
            onValueChange = {},
            readOnly = true,
            enabled = options.isNotEmpty(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let {
                { Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            trailingIcon = { TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { option ->
                val checked = option in selected

                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = checked,
                                // The row already toggles; the box is a marker.
                                onCheckedChange = null
                            )
                            Text(text = valueConverter(option))
                        }
                    },
                    // No dismissing: picking one option should not close the menu.
                    onClick = { onToggle(option) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
