package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.ui.control.ExtraAction
import org.bibletranslationtools.wat.ui.control.PageType
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.bibletranslationtools.wat.ui.dialogs.LanguagesDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.logout
import wordanalysistool.composeapp.generated.resources.select_language_resource_type
import kotlin.uuid.ExperimentalUuidApi

class HomeScreen(private val user: User) : Screen {

    @OptIn(ExperimentalUuidApi::class)
    @Composable
    override fun Content() {

        val viewModel = koinScreenModel<HomeViewModel>()
        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(HomeEvent.Idle)

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        var selectedHeartLanguage by remember { mutableStateOf<LanguageInfo?>(null) }
        var selectedResourceType by remember { mutableStateOf<String?>(null) }
        var showLanguagesDialog by remember { mutableStateOf(false) }

        LaunchedEffect(selectedHeartLanguage) {
            selectedHeartLanguage?.let {
                viewModel.onEvent(HomeEvent.FetchResourceTypes(it.ietfCode))
            }
        }

        selectedHeartLanguage?.let { language ->
            selectedResourceType?.let { resourceType ->
                if (state.verses.isNotEmpty()) {
                    navigator.push(
                        AnalyzeScreen(language, resourceType, state.verses, user)
                    )
                    viewModel.onEvent(HomeEvent.OnBeforeNavigate)
                }
            }
        }

        Scaffold(
            topBar = {
                TopNavigationBar(
                    title = "",
                    user = user,
                    page = PageType.HOME,
                    ExtraAction(
                        title = stringResource(Res.string.logout),
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = {
                            accessToken = null
                            navigator.pop()
                        }
                    )
                )
            },
            floatingActionButton = {
                Button(
                    onClick = { showLanguagesDialog = true },
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
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(32.dp)
            ) {
                Text(
                    text = stringResource(Res.string.select_language_resource_type),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (showLanguagesDialog) {
                LanguagesDialog(
                    languages = state.heartLanguages,
                    resourceTypes = state.resourceTypes,
                    onLanguageSelected = { selectedHeartLanguage = it },
                    onResourceTypeSelected = { language, resourceType ->
                        selectedResourceType = resourceType
                        viewModel.onEvent(HomeEvent.FetchUsfm(language.ietfCode, resourceType))
                    },
                    onDismiss = { showLanguagesDialog = false }
                )
            }

            state.alert?.let {
                AlertDialog(
                    message = it.message,
                    onDismiss = it.onClosed
                )
            }

            state.progress?.let {
                ProgressDialog(it)
            }
        }
    }
}