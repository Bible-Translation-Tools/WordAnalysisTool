package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.domain.BatchProgress
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.likely_correct
import wordanalysistool.shared.generated.resources.likely_incorrect
import wordanalysistool.shared.generated.resources.review_needed
import wordanalysistool.shared.generated.resources.total_singletons

@Composable
fun BatchInfo(
    info: BatchProgress?,
    modifier: Modifier = Modifier
) {
    val reviewedProgress = info?.let {
        val completed = (info.correct + it.incorrect).toFloat()
        if (completed > 0) info.reviewed / completed else 0f
    } ?: 0f

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { reviewedProgress },
                modifier = Modifier.weight(1f),
                gapSize = 0.dp
            )
            Text(
                text = "${(reviewedProgress * 100).toInt()}%",
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.likely_incorrect))
            Text(text = info?.incorrect?.toString() ?: "0")
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.likely_correct))
            Text(text = info?.correct?.toString() ?: "0")
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.review_needed))
            Text(text = info?.reviewNeeded?.toString() ?: "0")
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(Res.string.total_singletons),
                fontSize = 16.sp
            )
            Text(
                text = info?.let { "${it.completed}/${it.total}" } ?: "0/0",
                fontWeight = FontWeight.Bold
            )
        }
    }
}