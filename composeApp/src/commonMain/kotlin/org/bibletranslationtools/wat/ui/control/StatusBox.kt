package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.domain.BatchError
import org.bibletranslationtools.wat.ui.Status


@Composable
fun StatusBox(
    statuses: List<Status>,
    onShowError: (BatchError) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth(0.35f)
            .fillMaxHeight(0.6f)
            .offset(y = (-46).dp, x = (-10).dp)
            .shadow(4.dp, RoundedCornerShape(8.dp))
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            items(statuses) { status ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val message = if (status.info is BatchError) status.info.message else status.info
                    Text(
                        text = "${status.time} $message",
                        fontSize = 12.sp
                    )
                    if (status.info is BatchError) {
                        IconButton(
                            onClick = { onShowError(status.info) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                tint = MaterialTheme.colorScheme.error,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}