package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        Box(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = { showDialog = true },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("Select language and resource type")
            }
        }

        if (showDialog) {
            LanguagesDialog(
                languages = heartLanguages,
                resourceTypes = resourceTypes,
                onLanguageSelected = { selectedHeartLanguage = it },
                onResourceTypeSelected = { language, resourceType ->
                    CoroutineScope(Dispatchers.Default).launch {
                        val verses =
                            viewModel.fetchUsfmForHeartLanguage(language.ietfCode, resourceType)
                        if (verses.isNotEmpty()) {
                            navigator.push(AnalyzeScreen(verses))
                        }
                    }
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