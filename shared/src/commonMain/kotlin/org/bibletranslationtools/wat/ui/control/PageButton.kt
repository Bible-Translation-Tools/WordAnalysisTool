package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun PageButton(
    page: Int,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .sizeIn(
                minWidth = 40.dp,
                minHeight = 40.dp,
                maxHeight = 40.dp
            )
            .clip(MaterialTheme.shapes.small)
            .then(
                if (enabled && !active) Modifier.clickable {
                    onClick(page)
                } else Modifier
            )
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.5f
            }
            .background(
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            )
            .border(
                width = 1.dp,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = page.toString(),
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else MaterialTheme.colorScheme.onSurface
        )
    }
}