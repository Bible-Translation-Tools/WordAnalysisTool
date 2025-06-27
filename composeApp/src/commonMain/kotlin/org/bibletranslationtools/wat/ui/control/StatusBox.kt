package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusBox(
    statuses: List<String>,
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
                Text(
                    text = status,
                    fontSize = 12.sp
                )
            }
        }
    }
}