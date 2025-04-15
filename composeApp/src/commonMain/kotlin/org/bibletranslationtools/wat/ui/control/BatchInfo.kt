package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.SingletonWord
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.likely_correct
import wordanalysistool.composeapp.generated.resources.likely_incorrect
import wordanalysistool.composeapp.generated.resources.names
import wordanalysistool.composeapp.generated.resources.review_needed
import wordanalysistool.composeapp.generated.resources.total_singletons
import wordanalysistool.composeapp.generated.resources.word_analysis

@Composable
fun BatchInfo(singletons: List<SingletonWord>) {
    val progress = if (singletons.isNotEmpty()) {
        singletons.filter { it.correct != null }.size / singletons.size.toFloat()
    } else 0f

    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(Res.string.word_analysis),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp)
            )
            Text(stringResource(Res.string.likely_incorrect))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = singletons.filter {
                it.result?.consensus == Consensus.LIKELY_INCORRECT
            }.size.toString())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(12.dp)
            )
            Text(stringResource(Res.string.review_needed))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = singletons.filter {
                it.result?.consensus == Consensus.NEEDS_REVIEW
            }.size.toString())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(12.dp)
            )
            Text(stringResource(Res.string.likely_correct))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = singletons.filter {
                it.result?.consensus == Consensus.LIKELY_CORRECT
            }.size.toString())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Text(stringResource(Res.string.names))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = singletons.filter {
                it.result?.consensus == Consensus.NAME
            }.size.toString())
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = stringResource(Res.string.total_singletons),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(text = singletons.size.toString())
        }
    }
}