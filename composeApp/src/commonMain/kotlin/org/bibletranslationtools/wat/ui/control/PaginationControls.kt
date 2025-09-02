package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.back
import wordanalysistool.composeapp.generated.resources.page_info
import wordanalysistool.composeapp.generated.resources.save
import wordanalysistool.composeapp.generated.resources.save_next

@Composable
fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    enabled: Boolean = true,
    onPageSelected: (Int) -> Unit,
    onSaveAndNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onPageSelected(currentPage - 1) },
            enabled = enabled && currentPage > 1,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
            ),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "back"
            )
            Text(stringResource(Res.string.back))
        }

        if (totalPages > 0) {
            Text(
                text = stringResource(
                    Res.string.page_info,
                    currentPage,
                    totalPages
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(
            onClick = onSaveAndNext,
            shape = MaterialTheme.shapes.small,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(start = 8.dp)
        ) {
            Text(
                text = if (currentPage < totalPages) {
                    stringResource(Res.string.save_next)
                } else stringResource(Res.string.save)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "save & next"
            )
        }
    }
}