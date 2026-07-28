package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.first_word
import wordanalysistool.shared.generated.resources.last_word
import wordanalysistool.shared.generated.resources.next_word
import wordanalysistool.shared.generated.resources.previous_word

/** Left half of the card navigation: jump to the first card, or step back one. */
@Composable
fun PrevCardNavigation(
    enabled: Boolean,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        CardNavButton(
            icon = Icons.Default.ChevronLeft,
            description = stringResource(Res.string.first_word),
            enabled = enabled,
            onClick = onFirst
        )
        CardNavButton(
            icon = Icons.Default.KeyboardDoubleArrowLeft,
            description = stringResource(Res.string.previous_word),
            enabled = enabled,
            onClick = onPrev
        )
    }
}

/** Right half of the card navigation: step forward one card, or jump to the last. */
@Composable
fun NextCardNavigation(
    enabled: Boolean,
    onNext: () -> Unit,
    onLast: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        CardNavButton(
            icon = Icons.Default.KeyboardDoubleArrowRight,
            description = stringResource(Res.string.next_word),
            enabled = enabled,
            onClick = onNext
        )
        CardNavButton(
            icon = Icons.Default.ChevronRight,
            description = stringResource(Res.string.last_word),
            enabled = enabled,
            onClick = onLast
        )
    }
}

@Composable
private fun CardNavButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
