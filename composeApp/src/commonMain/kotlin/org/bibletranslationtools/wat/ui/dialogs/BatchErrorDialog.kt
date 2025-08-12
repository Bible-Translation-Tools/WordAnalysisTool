package org.bibletranslationtools.wat.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.bibletranslationtools.wat.domain.BatchError
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.response_info
import wordanalysistool.composeapp.generated.resources.model_info
import wordanalysistool.composeapp.generated.resources.ok
import wordanalysistool.composeapp.generated.resources.prompt_info

@Composable
fun BatchErrorDialog(
    error: BatchError,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(MaterialTheme.shapes.large)
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(Res.string.prompt_info), fontWeight = FontWeight.Bold)
                        Text(if (!error.prompt.isNullOrEmpty()) error.prompt else "n/a")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(Res.string.model_info), fontWeight = FontWeight.Bold)
                        Text(error.model ?: "n/a")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(Res.string.response_info), fontWeight = FontWeight.Bold)
                        Text(error.response ?: "n/a")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.width(128.dp)
                ) {
                    Text(stringResource(Res.string.ok))
                }
            }
        }
    }
}