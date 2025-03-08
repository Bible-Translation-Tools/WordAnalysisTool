package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.ui.dialogs.ErrorDialog
import org.bibletranslationtools.wat.ui.dialogs.LanguagesDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog

class HomeScreen : Screen {

    @Composable
    override fun Content() {

        val viewModel = koinScreenModel<HomeViewModel>()
        val navigator = LocalNavigator.currentOrThrow

        val heartLanguages by viewModel.heartLanguages.collectAsStateWithLifecycle(emptyList())
        val usfmForHeartLanguage by viewModel.usfmForHeartLanguage.collectAsStateWithLifecycle()
        val verses by viewModel.verses.collectAsStateWithLifecycle()

        var selectedHeartLanguage by remember { mutableStateOf<LanguageInfo?>(null) }
        var resourceTypes by remember { mutableStateOf<List<String>>(emptyList()) }
        var showDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            viewModel.fetchHeartLanguages()
        }

        LaunchedEffect(selectedHeartLanguage) {
            selectedHeartLanguage?.let {
                resourceTypes = viewModel.fetchResourceTypesForHeartLanguage(it.ietfCode)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = { showDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Select language")
            }

            if (verses.isEmpty()) {
                LazyColumn {
                    items(usfmForHeartLanguage) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.clickable {
                                it.url?.let { viewModel.fetchUsfm(it) }
                            }
                        ) {
                            Text(it.bookSlug ?: "n/a")
                            Text(it.bookName ?: "n/a")
                            Text(it.url ?: "n/a")
                        }
                    }
                }
            } else {
                LazyColumn {
                    items(verses) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${it.bookName} ${it.chapter}:${it.number}. ${it.text}")
                        }
                    }
                }
            }
        }

        if (showDialog) {
            LanguagesDialog(
                languages = heartLanguages,
                resourceTypes = resourceTypes,
                onLanguageSelected = { selectedHeartLanguage = it },
                onResourceTypeSelected = { language, resourceType ->
                    viewModel.clearVerses()
                    viewModel.fetchUsfmForHeartLanguage(language.ietfCode, resourceType)
                },
                onDismiss = { showDialog = false }
            )
        }

        viewModel.error?.let {
            ErrorDialog(error = it, onDismiss = { viewModel.clearError() })
        }

        viewModel.progress?.let {
            ProgressDialog(it)
        }
    }
}