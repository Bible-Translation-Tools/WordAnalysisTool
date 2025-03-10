package org.bibletranslationtools.wat.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.apply
import wordanalysistool.composeapp.generated.resources.edit_prompt

@Composable
fun PromptEditorDialog(
    prompt: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textFieldValue by remember { mutableStateOf(prompt) }

    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
                    .fillMaxHeight()
            ) {
                Text(
                    text = stringResource(Res.string.edit_prompt),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                TextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                )
                Button(
                    onClick = {
                        onConfirm(textFieldValue)
                        onDismiss()
                    }
                ) {
                    Text(stringResource(Res.string.apply))
                }
            }
        }
    }
}