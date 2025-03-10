package org.bibletranslationtools.wat.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DrawerDefaults.shape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.bibletranslationtools.wat.data.LanguageInfo
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.dismiss
import wordanalysistool.composeapp.generated.resources.no_resource_types
import wordanalysistool.composeapp.generated.resources.search_language
import wordanalysistool.composeapp.generated.resources.select_resource_type

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagesDialog(
    languages: List<LanguageInfo>,
    resourceTypes: List<String>,
    onLanguageSelected: (LanguageInfo) -> Unit,
    onResourceTypeSelected: (LanguageInfo, String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchTextState by remember { mutableStateOf(TextFieldValue("")) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var filteredLanguages by remember { mutableStateOf(languages) }
    var selectedLanguage by remember { mutableStateOf<LanguageInfo?>(null) }

    var resourceMenuExpanded by remember { mutableStateOf(false) }
    var selectedResourceType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchTextState) {
        filteredLanguages = if (searchTextState.text.isBlank()) {
            languages.take(5) // Show initial 5 if search is empty
        } else {
            languages.filter {
                it.toString().contains(searchTextState.text, ignoreCase = true)
            }
                .take(100) // Limit to 100 results for optimization
                .sortedBy { it.ietfCode }
        }
    }

    LaunchedEffect(resourceTypes, selectedLanguage) {
        selectedResourceType = when {
            resourceTypes.size == 1 -> resourceTypes.first()
            resourceTypes.isNotEmpty() -> getString(Res.string.select_resource_type)
            selectedLanguage != null -> getString(Res.string.no_resource_types)
            else -> ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
                    .padding(32.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = languageMenuExpanded && filteredLanguages.isNotEmpty(),
                    onExpandedChange = { languageMenuExpanded = !languageMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchTextState,
                        label = { Text(stringResource(Res.string.search_language)) },
                        onValueChange = { searchTextState = it },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = languageMenuExpanded
                            )
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                            .fillMaxWidth(),
                    )

                    ExposedDropdownMenu(
                        expanded = languageMenuExpanded && filteredLanguages.isNotEmpty(),
                        onDismissRequest = { languageMenuExpanded = false }
                    ) {
                        filteredLanguages
                            .forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.toString()) },
                                    onClick = {
                                        searchTextState = TextFieldValue(language.toString())
                                        languageMenuExpanded = false
                                        selectedLanguage = language
                                        onLanguageSelected(language)
                                    }
                                )
                            }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = resourceMenuExpanded && resourceTypes.isNotEmpty(),
                    onExpandedChange = { resourceMenuExpanded = !resourceMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedResourceType ?: run {
                            if (selectedLanguage != null) {
                                stringResource(Res.string.no_resource_types)
                            } else {
                                ""
                            }
                        },
                        onValueChange = {},
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = resourceMenuExpanded && resourceTypes.isNotEmpty()
                            )
                        },
                        shape = shape,
                        colors = ExposedDropdownMenuDefaults.textFieldColors(
                            focusedIndicatorColor = Transparent,
                            unfocusedIndicatorColor = Transparent
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = resourceMenuExpanded && resourceTypes.isNotEmpty(),
                        onDismissRequest = { resourceMenuExpanded = false }
                    ) {
                        resourceTypes.forEach { type ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedResourceType = type
                                    resourceMenuExpanded = false
                                },
                                text = { Text(type) }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onDismiss
                    ) {
                        Text(stringResource(Res.string.dismiss))
                    }
                    Button(
                        onClick = {
                            selectedLanguage?.let { language ->
                                selectedResourceType?.let { resourceType ->
                                    onResourceTypeSelected(language, resourceType)
                                }
                            }
                            onDismiss()
                        }
                    ) {
                        Text("Show USFM")
                    }
                }
            }
        }
    }
}