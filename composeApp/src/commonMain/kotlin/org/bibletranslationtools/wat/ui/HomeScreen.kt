package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.ErrorDialog
import org.bibletranslationtools.wat.ui.dialogs.LanguagesDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.select_language_resource_type

class HomeScreen : Screen {

    @Composable
    override fun Content() {

        val viewModel = koinScreenModel<HomeViewModel>()
        val navigator = LocalNavigator.currentOrThrow

        val heartLanguages by viewModel.heartLanguages.collectAsStateWithLifecycle(emptyList())

        var selectedHeartLanguage by remember { mutableStateOf<LanguageInfo?>(null) }
        var resourceTypes by remember { mutableStateOf<List<String>>(emptyList()) }
        var showDialog by remember { mutableStateOf(false) }

        LaunchedEffect(selectedHeartLanguage) {
            selectedHeartLanguage?.let {
                resourceTypes = viewModel.fetchResourceTypesForHeartLanguage(it.ietfCode)
            }
        }

        Scaffold(
            topBar = {
                TopNavigationBar("", isHome = true)
            },
            floatingActionButton = {
                Button(
                    onClick = { showDialog = true },
                    shape = CircleShape,
                    modifier = Modifier.size(70.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(Res.string.select_language_resource_type),
                    modifier = Modifier.align(Alignment.Center)
                )
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
                                navigator.push(
                                    AnalyzeScreen(language, resourceType, verses)
                                )
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
}