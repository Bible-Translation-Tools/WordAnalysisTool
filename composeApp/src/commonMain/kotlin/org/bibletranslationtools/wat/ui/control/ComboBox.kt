import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class OptionIcon(
    val vector: ImageVector,
    val size: Dp = 18.dp,
    val tint: Color? = null
)

data class Option<T>(
    val value: T,
    val icon: OptionIcon? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ComboBox(
    value: T,
    options: List<Option<T>> = emptyList(),
    onOptionSelected: (T) -> Unit = {},
    valueConverter: (T) -> String = { it?.toString() ?: "" },
    label: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isEnabled by rememberUpdatedState { options.isNotEmpty() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (isEnabled()) {
                expanded = !expanded
            }
        },
        modifier = modifier,
    ) {
        TextField(
            enabled = isEnabled(),
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryEditable,
                    true
                ),
            readOnly = true,
            value = valueConverter(value),
            onValueChange = {},
            label = label?.let {{ Text(text = it) }},
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (option in options) {
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            option.icon?.let {
                                Icon(
                                    imageVector = it.vector,
                                    contentDescription = null,
                                    modifier = Modifier.size(it.size),
                                    tint = it.tint ?: LocalContentColor.current
                                )
                            }
                            Text(text = valueConverter(option.value))
                        }
                    },
                    onClick = {
                        onOptionSelected(option.value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
