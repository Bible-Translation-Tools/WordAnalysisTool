package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.background

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(model) }
                    .background(background)
                    .padding(8.dp)
            ) {
                Text(
                    text = valueConverter(model)
                )
            }
        }
    }
}