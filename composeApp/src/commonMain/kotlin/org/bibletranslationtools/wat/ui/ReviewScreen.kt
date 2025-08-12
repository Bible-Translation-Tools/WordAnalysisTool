package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.VerseRef
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.ui.control.CustomSnackBar
import org.bibletranslationtools.wat.ui.control.CustomTextButton
import org.bibletranslationtools.wat.ui.control.PaginationControls
import org.bibletranslationtools.wat.ui.control.SingletonRow
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.admin
import wordanalysistool.composeapp.generated.resources.flag_incorrect_words
import wordanalysistool.composeapp.generated.resources.home
import wordanalysistool.composeapp.generated.resources.loading
import wordanalysistool.composeapp.generated.resources.settings
import wordanalysistool.composeapp.generated.resources.sign_out
import wordanalysistool.composeapp.generated.resources.unflagged_marked_correct_message

class ReviewScreen(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: VerseRef,
    private val user: User,
    private val batchId: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<ReviewViewModel> {
            parametersOf(language, resourceType, verses, user, batchId)
        }

        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AnalyzeEvent.Idle)

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        val snackBarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val savedMassage = stringResource(Res.string.unflagged_marked_correct_message)

        LaunchedEffect(event) {
            when (event) {
                is AnalyzeEvent.Logout -> {
                    accessToken = null
                    navigator.popUntilRoot()
                }
                is ReviewEvent.Saved -> {
                    scope.launch {
                        snackBarHostState.showSnackbar(
                            message = savedMassage,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                else -> Unit
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    SnackbarHost(
                        hostState = snackBarHostState,
                        snackbar = { data ->
                            CustomSnackBar(snackBarHostState, data)
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                        .padding(16.dp)
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
                            Text(
                                text = language.name,
                                style = LocalTextStyle.current.copy(
                                    textDirection = TextDirection.ContentOrLtr,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.W500,
                                    fontFamily = getFontFamilyForText(language.name)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(Res.string.flag_incorrect_words),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(
                                    space = 4.dp,
                                    alignment = Alignment.Bottom
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                CustomTextButton(
                                    onClick = navigator::pop,
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
                                if (user.admin) {
                                    CustomTextButton(
                                        onClick = {
                                            navigator.push(AnalyzeScreen(
                                                language = language,
                                                resourceType = resourceType,
                                                verses = verses,
                                                user = user
                                            ))
                                        },
                                        icon = painterResource(Res.drawable.admin),
                                        text = stringResource(Res.string.admin)
                                    )
                                }
                                CustomTextButton(
                                    onClick = {
                                        accessToken = null
                                        navigator.popUntilRoot()
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
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LinearProgressIndicator(
                                    progress = { state.completeProgress },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(state.completeProgress*100).toInt()}%",
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(items = state.words, key = { it.word }) { singleton ->
                                    SingletonRow(
                                        singleton = singleton,
                                        enabled = !state.isLoading,
                                        onFlagged = { viewModel.onFlagClicked(singleton.word) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            PaginationControls(
                                currentPage = state.currentPage,
                                totalPages = state.totalPages,
                                enabled = !state.isLoading,
                                onPageSelected = { viewModel.onPageSelected(it) },
                                onSaveAndNext = { viewModel.onSaveAndNext() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (state.isLoading) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(
                                    12.dp,
                                    Alignment.CenterVertically
                                ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .size(width = 300.dp, height = 100.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.medium
                                    )
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(Res.string.loading),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            state.alert?.let {
                AlertDialog(
                    message = it.message,
                    onDismiss = it.onClosed
                )
            }
        }
    }
}
