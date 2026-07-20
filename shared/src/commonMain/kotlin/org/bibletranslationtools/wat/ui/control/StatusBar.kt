package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bibletranslationtools.wat.domain.BatchError

data class Status(
    val info: Any,
    val time: String
)

@Composable
fun StatusBar(
    status: Status?,
    onToggleStatusBox: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .height(36.dp)
                .padding(start = 16.dp)
        ) {
            val message = status?.let {
                when (it.info) {
                    is String -> it.info
                    is BatchError -> it.info.message
                    else -> null
                }
            } ?: ""
            Text(
                text = message,
                fontSize = 12.sp
            )
            IconButton(onClick = onToggleStatusBox) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null)
            }
        }
    }
}