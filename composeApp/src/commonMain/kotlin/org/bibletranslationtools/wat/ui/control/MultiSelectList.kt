package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun <T> MultiSelectList(
    items: List<T>,
    selected: List<T>,
    valueConverter: (T) -> String = { it?.toString() ?: "" },
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        items.forEach { model ->
            val background = if (model in selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else MaterialTheme.colorScheme.surface
            val border = if (model in selected) {
                MaterialTheme.colorScheme.primary
            } else Color.Transparent

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(model) }
                    .background(background)
                    .border(
                        width = 1.dp,
                        color = border,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = valueConverter(model),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}