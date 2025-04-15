package org.bibletranslationtools.wat.ui.dialogs

import ComboBox
import Option
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.ui.control.SearchableComboBox
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.analyze
import wordanalysistool.composeapp.generated.resources.dismiss
import wordanalysistool.composeapp.generated.resources.no_resource_types
import wordanalysistool.composeapp.generated.resources.search_language

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesDialog(
    languages: List<LanguageInfo>,
    resourceTypes: List<String>,
    onLanguageSelected: (LanguageInfo) -> Unit,
    onResourceTypeSelected: (LanguageInfo, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf<LanguageInfo?>(null) }
    var selectedResourceType by remember { mutableStateOf<String?>(null) }
    var analyzeEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(resourceTypes, selectedLanguage) {
        when {
            resourceTypes.isNotEmpty() -> {
                selectedResourceType = resourceTypes.first()
                analyzeEnabled = true
            }
            selectedLanguage != null -> {
                selectedResourceType = getString(Res.string.no_resource_types)
                analyzeEnabled = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.7f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
                    .padding(32.dp)
            ) {
                SearchableComboBox(
                    label = stringResource(Res.string.search_language),
                    options = languages.sortedBy { it.ietfCode },
                    onOptionSelected = {
                        selectedLanguage = it
                        onLanguageSelected(it)
                    },
                    valueConverter = { it.toString() },
                    modifier = Modifier.fillMaxWidth()
                )

                ComboBox(
                    value = selectedResourceType,
                    options = resourceTypes.map(::Option),
                    onOptionSelected = { selectedResourceType = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.dismiss),
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                    Button(
                        onClick = {
                            selectedLanguage?.let { language ->
                                selectedResourceType?.let { resourceType ->
                                    onResourceTypeSelected(language, resourceType)
                                }
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = analyzeEnabled
                    ) {
                        Text(
                            text = stringResource(Res.string.analyze),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}