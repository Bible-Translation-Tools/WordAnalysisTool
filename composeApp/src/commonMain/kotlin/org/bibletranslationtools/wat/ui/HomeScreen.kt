package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
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
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.ui.control.ExtraAction
import org.bibletranslationtools.wat.ui.control.TopNavigationBar
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.bibletranslationtools.wat.ui.dialogs.LanguagesDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.creator
import wordanalysistool.composeapp.generated.resources.language
import wordanalysistool.composeapp.generated.resources.logout
import wordanalysistool.composeapp.generated.resources.resource_type
import kotlin.uuid.ExperimentalUuidApi

class HomeScreen(private val user: User) : Screen {

    @OptIn(ExperimentalUuidApi::class)
    @Composable
    override fun Content() {

        val viewModel = koinScreenModel<HomeViewModel> {
            parametersOf(user)
        }
        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        var selectedHeartLanguage by remember { mutableStateOf<LanguageInfo?>(null) }
        var showLanguagesDialog by remember { mutableStateOf(false) }

        LaunchedEffect(selectedHeartLanguage) {
            selectedHeartLanguage?.let {
                viewModel.onEvent(HomeEvent.FetchResourceTypes(it.ietfCode))
            }
        }

        Scaffold(
            topBar = {
                TopNavigationBar(
                    title = "",
                    user = user,
                    ExtraAction(
                        title = stringResource(Res.string.logout),
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = {
                            accessToken = null
                            UrlManager.replaceAll(LoginScreen())
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
                Column(
                    modifier = Modifier.fillMaxSize(0.7f)
                        .align(Alignment.Center)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(Res.string.language))
                        Text(stringResource(Res.string.resource_type))
                        Text(stringResource(Res.string.creator))
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .shadow(
                                elevation = 4.dp,
                                shape = MaterialTheme.shapes.medium
                            )
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            items(state.batches) { batch ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .height(50.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable {
                                            UrlManager.push(
                                                ReviewScreen(
                                                    ietfCode = batch.language.ietfCode,
                                                    resourceType = batch.resourceType,
                                                    user = user,
                                                    batchId = batch.id
                                                )
                                            )
                                        }
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = batch.language.toString(),
                                        modifier = Modifier.weight(0.34f),
                                        fontFamily = getFontFamilyForText(
                                            batch.language.toString()
                                        )
                                    )
                                    Text(
                                        text = batch.resourceType,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(0.33f)
                                    )
                                    Text(
                                        text = batch.username,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.33f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showLanguagesDialog) {
                LanguagesDialog(
                    languages = state.heartLanguages,
                    resourceTypes = state.resourceTypes,
                    onLanguageSelected = { selectedHeartLanguage = it },
                    onResourceTypeSelected = { language, resourceType ->
                        UrlManager.push(
                            ReviewScreen(
                                ietfCode = language.ietfCode,
                                resourceType = resourceType,
                                user = user
                            )
                        )
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