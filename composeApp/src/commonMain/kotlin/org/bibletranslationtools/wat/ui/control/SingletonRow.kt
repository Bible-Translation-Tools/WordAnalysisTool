package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.Direction
import org.bibletranslationtools.wat.data.SingletonWord

@Composable
fun SingletonRow(
    singleton: SingletonWord,
    selected: Boolean,
    direction: Direction,
    onSelect: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable { onSelect() }
    ) {
        if (direction == Direction.LTR) {
            renderText(singleton, selected)
            renderIcon(singleton.correct)
        } else {
            renderIcon(singleton.correct)
            renderText(singleton, selected)
        }
    }
}

@Composable
private fun renderIcon(correct: Boolean?) {
    if (correct != null) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun renderText(
    singleton: SingletonWord,
    selected: Boolean
) {
    Text(
        text = singleton.word,
        fontWeight = if (selected)
            FontWeight.Bold else FontWeight.Normal,
        color = when (singleton.result?.consensus) {
            Consensus.LIKELY_INCORRECT -> MaterialTheme.colorScheme.error
            Consensus.LIKELY_CORRECT -> MaterialTheme.colorScheme.tertiary
            Consensus.NAME -> MaterialTheme.colorScheme.secondary
            Consensus.NEEDS_REVIEW -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onBackground
        },
        style = LocalTextStyle.current.copy(
            textDirection = TextDirection.ContentOrLtr
        )
    )
}