package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
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
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
            )
        ) {
            Text(stringResource(Res.string.back))
        }

        val pagesToShow = 5
        val startPage = (currentPage - pagesToShow / 2)
            .coerceIn(1, (totalPages - pagesToShow + 1).coerceAtLeast(1))
        val endPage = (startPage + pagesToShow - 1)
            .coerceAtMost(totalPages)

        if (startPage > 1) {
            PageButton(
                page = 1,
                enabled = enabled,
                active = false,
                onClick = { onPageSelected(1) }
            )
            if (startPage > 2) Text("...")
        }

        for (i in startPage..endPage) {
            val isCurrent = i == currentPage
            PageButton(
                page = i,
                enabled = enabled,
                active = isCurrent,
                onClick = { onPageSelected(i) }
            )
        }

        if (endPage < totalPages) {
            if (endPage < totalPages - 1) Text("...")
            PageButton(
                page = totalPages,
                enabled = enabled,
                active = false,
                onClick = { onPageSelected(totalPages) }
            )
        }

        OutlinedButton(
            onClick = onSaveAndNext,
            shape = MaterialTheme.shapes.small,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
        ) {
            Text(
                text = if (currentPage < totalPages) {
                    stringResource(Res.string.save_next)
                } else stringResource(Res.string.save)
            )
        }
    }
}