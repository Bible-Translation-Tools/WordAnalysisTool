package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.burnoo.compose.remembersetting.rememberBooleanSetting
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.VerseRef
import org.bibletranslationtools.wat.domain.BatchError
import org.bibletranslationtools.wat.domain.Model
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.ui.control.BatchInfo
import org.bibletranslationtools.wat.ui.control.BatchProgress
import org.bibletranslationtools.wat.ui.control.CustomTextButton
import org.bibletranslationtools.wat.ui.control.Status
import org.bibletranslationtools.wat.ui.control.StatusBar
import org.bibletranslationtools.wat.ui.control.StatusBox
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.bibletranslationtools.wat.ui.dialogs.BatchErrorDialog
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.admin
import wordanalysistool.composeapp.generated.resources.back
import wordanalysistool.composeapp.generated.resources.delete_batch
import wordanalysistool.composeapp.generated.resources.home
import wordanalysistool.composeapp.generated.resources.pause_batch
import wordanalysistool.composeapp.generated.resources.process_words
import wordanalysistool.composeapp.generated.resources.save_report
import wordanalysistool.composeapp.generated.resources.settings
import wordanalysistool.composeapp.generated.resources.sign_out

class AnalyzeScreen(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: VerseRef,
    private val user: User
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<AnalyzeViewModel> {
            parametersOf(language, resourceType, verses, user)
        }

        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AnalyzeEvent.Idle)

        val modelsState = Model.entries.mapNotNull {
            val active = rememberBooleanSetting(it.value, false).value
            if (active) it.value else null
        }.toMutableStateList()
        val models = remember { modelsState }

        val apostropheIsSeparator by rememberBooleanSetting(
            Settings.APOSTROPHE_IS_SEPARATOR.name,
            true
        )

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        val statuses = remember { mutableStateListOf<Status>() }
        var showStatuses by remember { mutableStateOf(false) }
        var batchError by remember { mutableStateOf<BatchError?>(null) }

        LaunchedEffect(event) {
            when (event) {
                is AnalyzeEvent.Logout -> {
                    accessToken = null
                    navigator.popUntilRoot()
                }
                else -> Unit
            }
        }

        LaunchedEffect(models) {
            if (models.isNotEmpty()) {
                viewModel.onEvent(AnalyzeEvent.UpdateModels(models))
            }
        }

        LaunchedEffect(apostropheIsSeparator) {
            viewModel.onEvent(AnalyzeEvent.FindSingletons(apostropheIsSeparator))
        }

        LaunchedEffect(state.status) {
            state.status?.let {
                if (statuses.size > 1_000) {
                    statuses.removeAt(0)
                }
                statuses.add(it)
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium
                            )
                            .weight(0.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CustomTextButton(
                                onClick = navigator::pop,
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                text = stringResource(Res.string.back),
                                modifier = Modifier.align(Alignment.End)
                            )

                            Text(
                                text = stringResource(Res.string.admin),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.W500
                            )
                            Text(
                                text = language.name,
                                style = LocalTextStyle.current.copy(
                                    textDirection = TextDirection.ContentOrLtr,
                                    fontFamily = getFontFamilyForText(language.name)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(
                                    space = 4.dp,
                                    alignment = Alignment.Bottom
                                )
                            ) {
                                CustomTextButton(
                                    onClick = {
                                        viewModel.onEvent(AnalyzeEvent.BatchWords)
                                    },
                                    icon = Icons.Default.Sync,
                                    text = stringResource(Res.string.process_words)
                                )
                                CustomTextButton(
                                    onClick = {
                                        viewModel.onEvent(AnalyzeEvent.PauseBatch)
                                    },
                                    icon = Icons.Default.Pause,
                                    text = stringResource(Res.string.pause_batch)
                                )
                                CustomTextButton(
                                    onClick = {
                                        viewModel.onEvent(AnalyzeEvent.DeleteBatch)
                                    },
                                    icon = Icons.Default.Delete,
                                    text = stringResource(Res.string.delete_batch)
                                )
                                CustomTextButton(
                                    onClick = {
                                        viewModel.onEvent(AnalyzeEvent.SaveReport)
                                    },
                                    icon = Icons.Default.Save,
                                    text = stringResource(Res.string.save_report)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(
                                    space = 4.dp,
                                    alignment = Alignment.Bottom
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                CustomTextButton(
                                    onClick = { navigator.popUntilRoot() },
                                    icon = Icons.Default.Home,
                                    text = stringResource(Res.string.home)
                                )
                                CustomTextButton(
                                    onClick = {
                                        navigator.push(SettingsScreen(user))
                                    },
                                    icon = Icons.Default.Settings,
                                    text = stringResource(Res.string.settings)
                                )
                                CustomTextButton(
                                    onClick = {
                                        accessToken = null
                                        UrlManager.replaceAll(LoginScreen())
                                    },
                                    icon = Icons.Default.Person,
                                    text = stringResource(
                                        Res.string.sign_out,
                                        user.username
                                    )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxHeight()
                            .padding(start = 48.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .width(500.dp)
                        ) {
                            BatchInfo(
                                info = state.batch?.details?.progress,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (state.batchProgress >= 0) {
                                BatchProgress(
                                    progress = state.batchProgress,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                StatusBar(
                    status = state.status ?: Status("", ""),
                    onToggleStatusBox = { showStatuses = !showStatuses },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                if (showStatuses) {
                    StatusBox(
                        statuses = statuses,
                        onShowError = { batchError = it },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            state.alert?.let {
                AlertDialog(
                    message = it.message,
                    onDismiss = it.onClosed
                )
            }

            batchError?.let {
                BatchErrorDialog(
                    error = it,
                    onDismiss = { batchError = null }
                )
            }

            state.progress?.let {
                ProgressDialog(it)
            }
        }
    }
}
