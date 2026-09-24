package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/** A step button's size, and the gap between the two in a group. */
internal val NAV_BUTTON_SIZE = 48.dp
internal val NAV_BUTTON_SPACING = 8.dp

/**
 * How far a group's step button sits from the group's centre. The step button is
 * the inner one, so a group centred this much beyond a card edge puts its step
 * button right on that edge.
 */
internal val NAV_STEP_OFFSET = (NAV_BUTTON_SIZE + NAV_BUTTON_SPACING) / 2

/** Left half of the card navigation: jump to the first card, or step back one. */
@Composable
fun PrevCardNavigation(
    enabled: Boolean,
    showFirst: Boolean,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(NAV_BUTTON_SPACING),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        JumpButton(
            icon = Icons.Default.KeyboardDoubleArrowLeft,
            description = stringResource(Res.string.first_word),
            visible = showFirst,
            onClick = onFirst
        )
        CardNavButton(
            icon = Icons.Default.ChevronLeft,
            description = stringResource(Res.string.previous_word),
            enabled = enabled,
            filled = true,
            onClick = onPrev
        )
    }
}

/** Right half of the card navigation: step forward one card, or jump to the last. */
@Composable
fun NextCardNavigation(
    enabled: Boolean,
    showLast: Boolean,
    onNext: () -> Unit,
    onLast: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(NAV_BUTTON_SPACING),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        CardNavButton(
            icon = Icons.Default.ChevronRight,
            description = stringResource(Res.string.next_word),
            enabled = enabled,
            filled = true,
            onClick = onNext
        )
        JumpButton(
            icon = Icons.Default.KeyboardDoubleArrowRight,
            description = stringResource(Res.string.last_word),
            visible = showLast,
            onClick = onLast
        )
    }
}

/**
 * A jump to one end of the carousel, shown only when there is somewhere to jump
 * to. It keeps its place in the row when hidden, so the step button beside it
 * does not move.
 */
@Composable
private fun JumpButton(
    icon: ImageVector,
    description: String,
    visible: Boolean,
    onClick: () -> Unit
) {
    if (!visible) {
        Spacer(modifier = Modifier.size(NAV_BUTTON_SIZE))
        return
    }

    CardNavButton(
        icon = icon,
        description = description,
        enabled = true,
        onClick = onClick
    )
}

/**
 * One round step. Stepping to the next or previous card is the ordinary move, so
 * it is [filled]; jumping to either end is outlined, offered but not urged.
 */
@Composable
private fun CardNavButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline

    // A step that cannot be taken drops its fill and greys out, the same way an
    // unavailable jump looks.
    val solid = filled && enabled

    val background = if (solid) accent else MaterialTheme.colorScheme.surface
    val border = when {
        solid -> null
        enabled -> BorderStroke(1.dp, accent)
        else -> BorderStroke(1.dp, muted)
    }
    val tint = when {
        solid -> MaterialTheme.colorScheme.onPrimary
        enabled -> accent
        else -> muted
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = background,
        border = border,
        modifier = Modifier.size(NAV_BUTTON_SIZE)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
