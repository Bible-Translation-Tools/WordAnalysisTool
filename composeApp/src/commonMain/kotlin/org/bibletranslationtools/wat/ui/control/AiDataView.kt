package org.bibletranslationtools.wat.ui.control

import ComboBox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.AiModel
import org.bibletranslationtools.wat.domain.ClaudeAiModel
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.OpenAiModel
import org.bibletranslationtools.wat.domain.QwenModel
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.active
import wordanalysistool.composeapp.generated.resources.api_key
import wordanalysistool.composeapp.generated.resources.get_one_here
import wordanalysistool.composeapp.generated.resources.model

@Composable
fun AiDataView(
    model : String,
    models: List<String>,
    aiApi: AiApi,
    apiKey: String,
    apiKeyLink: String,
    isActive: Boolean,
    onModelChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onActiveChanged: (Boolean) -> Unit
) {
    var apiKeyVisible by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        Column {
            Text(stringResource(Res.string.model))
            ComboBox(
                value = model,
                options = models,
                onOptionSelected = onModelChanged,
                valueConverter = { getModel(aiApi, it).value },
                modifier = Modifier.width(400.dp)
            )
        }
        Column {
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.api_key))
                    append(" ")

                    withLink(
                        LinkAnnotation.Url(
                            url = apiKeyLink,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = MaterialTheme.colorScheme.secondary)
                            )
                        )
                    ) {
                        append(stringResource(Res.string.get_one_here))
                    }
                }
            )
            TextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                visualTransformation = if (apiKeyVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (apiKeyVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible}){
                        Icon(imageVector  = image, null)
                    }
                },
                maxLines = 3,
                modifier = Modifier.width(400.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.active))
            Checkbox(
                checked = isActive,
                onCheckedChange = onActiveChanged,
                enabled = apiKey.isNotBlank()
            )
        }
    }
}

private fun <A> getModel(api: A, model: String): AiModel {
    return when (api) {
        AiApi.OPENAI -> OpenAiModel.getOrDefault(model)
        AiApi.QWEN -> QwenModel.getOrDefault(model)
        AiApi.CLAUDE_AI -> ClaudeAiModel.getOrDefault(model)
        else -> GeminiModel.getOrDefault(model)
    }
}