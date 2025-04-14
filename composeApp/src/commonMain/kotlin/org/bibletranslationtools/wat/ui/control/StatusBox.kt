package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusBox(
    statuses: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.35f)
            .fillMaxHeight(0.6f)
            .offset(y = (-40).dp, x = (-4).dp)
            .border(1.dp, MaterialTheme.colorScheme.onBackground)
    ) {
        LazyColumn(
            modifier = Modifier.padding(8.dp)
        ) {
            items(statuses) { status ->
                Text(
                    text = status,
                    fontSize = 12.sp
                )
            }
        }
    }
}