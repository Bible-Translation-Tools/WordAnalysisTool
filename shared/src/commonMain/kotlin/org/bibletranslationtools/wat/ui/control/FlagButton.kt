package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.flag
import wordanalysistool.shared.generated.resources.flag_filled

@Composable
fun FlagButton(
    flagged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val border = if (flagged) {
        MaterialTheme.colorScheme.error
    } else MaterialTheme.colorScheme.outline

    val background = if (flagged) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    } else MaterialTheme.colorScheme.surface

    val icon = if (flagged) {
        Res.drawable.flag_filled
    } else Res.drawable.flag

    val iconColor = if (flagged) {
        MaterialTheme.colorScheme.error
    } else MaterialTheme.colorScheme.onBackground

    Surface(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, border),
        color = background,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .padding(0.dp)
            .width(54.dp)
            .height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = "flag",
                tint = iconColor
            )
        }
    }
}