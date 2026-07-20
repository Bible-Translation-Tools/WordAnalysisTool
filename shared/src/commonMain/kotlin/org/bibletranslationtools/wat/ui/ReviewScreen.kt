package org.bibletranslationtools.wat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
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
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.ui.control.CustomTextButton
import org.bibletranslationtools.wat.ui.control.MessageToast
import org.bibletranslationtools.wat.ui.control.PaginationControls
import org.bibletranslationtools.wat.ui.control.SingletonRow
import org.bibletranslationtools.wat.ui.dialogs.ProgressDialog
import org.bibletranslationtools.wat.ui.theme.getFontFamilyForText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.admin
import wordanalysistool.shared.generated.resources.app_name
import wordanalysistool.shared.generated.resources.back
import wordanalysistool.shared.generated.resources.complete_success
import wordanalysistool.shared.generated.resources.complete_success_description
import wordanalysistool.shared.generated.resources.flag
import wordanalysistool.shared.generated.resources.flag_incorrect_words
import wordanalysistool.shared.generated.resources.home
import wordanalysistool.shared.generated.resources.loading
import wordanalysistool.shared.generated.resources.return_home
import wordanalysistool.shared.generated.resources.settings
import wordanalysistool.shared.generated.resources.sign_out

class ReviewScreen(
    val ietfCode: String,
    val resourceType: String,
    private val user: User,
    private val batchId: String? = null
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<ReviewViewModel> {
            parametersOf(ietfCode, resourceType, user, batchId)
        }

        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AnalyzeEvent.Idle)

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        val listState = rememberLazyListState()

        LaunchedEffect(event) {
            when (event) {
                is ReviewEvent.Logout -> {
                    accessToken = null
                    navigator.popUntilRoot()
                }
                else -> Unit
            }
        }

        LaunchedEffect(state.currentPage) {
            listState.scrollToItem(0)
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(0.7f)
                        .fillMaxHeight()
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
                                text = stringResource(Res.string.app_name),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.W500,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.language?.name ?: "",
                                style = LocalTextStyle.current.copy(
                                    textDirection = TextDirection.ContentOrLtr,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.W600,
                                    fontFamily = getFontFamilyForText(state.language?.name ?: ""),
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(
                                    space = 4.dp,
                                    alignment = Alignment.Bottom
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                CustomTextButton(
                                    onClick = UrlManager::pop,
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
                                                ietfCode = ietfCode,
                                                resourceType = resourceType,
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
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.words.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize()
                                    .padding(horizontal = 32.dp)
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { state.completeProgress },
                                        modifier = Modifier.weight(1f),
                                        gapSize = 0.dp
                                    )
                                    Text(
                                        text = "${(state.completeProgress*100).toInt()}%",
                                    )
                                }

                                if (state.completeProgress < 1.0) {
                                    val placeholder = "[flag]"
                                    val inlineContentId = "flagIconId"
                                    val rawText = stringResource(
                                        Res.string.flag_incorrect_words,
                                        placeholder
                                    )
                                    val text = buildAnnotatedString {
                                        val index = rawText.indexOf(placeholder)
                                        if (index != -1) {
                                            append(rawText.take(index))
                                            appendInlineContent(inlineContentId, placeholder)
                                            append(rawText.substring(
                                                index + placeholder.length,
                                                rawText.length
                                            ))
                                        } else {
                                            append(rawText)
                                        }
                                    }
                                    val inlineContentMap = mapOf(
                                        inlineContentId to InlineTextContent(
                                            Placeholder(
                                                width = 52.sp,
                                                height = 32.sp,
                                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize()
                                                    .border(
                                                        border = BorderStroke(
                                                            1.dp,
                                                            MaterialTheme.colorScheme.outline
                                                        ),
                                                        shape = MaterialTheme.shapes.large,
                                                    )
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surface,
                                                        shape = MaterialTheme.shapes.large
                                                    )
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                        .padding(4.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(Res.drawable.flag),
                                                        contentDescription = "flag",
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(32.dp))

                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        border = BorderStroke(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = text,
                                                inlineContent = inlineContentMap,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.W400,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(32.dp))

                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(64.dp),
                                        state = listState,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(items = state.words, key = { it.word }) { singleton ->
                                            SingletonRow(
                                                singleton = singleton,
                                                enabled = !state.isLoading,
                                                onFlagged = {
                                                    viewModel.onFlagClicked(singleton.word)
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    PaginationControls(
                                        currentPage = state.currentPage,
                                        totalPages = state.totalPages,
                                        enabled = !state.isLoading,
                                        onSave = viewModel::onSave,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Spacer(modifier = Modifier.height(1.dp))

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "competed",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(60.dp)
                                            )
                                            Text(
                                                text = stringResource(Res.string.complete_success),
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.W500
                                            )
                                            Text(
                                                text = stringResource(Res.string.complete_success_description),
                                                fontSize = 16.sp
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = {  },
                                                enabled = false,
                                                shape = MaterialTheme.shapes.small,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                                ),
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outline
                                                ),
                                                contentPadding = PaddingValues(end = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ChevronLeft,
                                                    contentDescription = "back"
                                                )
                                                Text(stringResource(Res.string.back))
                                            }

                                            OutlinedButton(
                                                onClick = UrlManager::pop,
                                                shape = MaterialTheme.shapes.small,
                                                enabled = true,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.return_home)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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

                AnimatedVisibility(
                    visible = state.toast != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 64.dp)
                ) {
                    state.toast?.let { data ->
                        MessageToast(
                            type = data.type,
                            message = data.message,
                            onDismiss = data.onClose
                        )
                    }
                }
            }

            state.progress?.let {
                ProgressDialog(it)
            }
        }
    }
}
