package org.bibletranslationtools.wat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Token
import org.bibletranslationtools.wat.navigation.UrlManager
import org.bibletranslationtools.wat.ui.control.MessageToast
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.login
import wordanalysistool.shared.generated.resources.login_progress

class LoginScreen(private val initialPath: String? = null) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<LoginViewModel>()
        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AnalyzeEvent.Idle)

        val uriHandler = LocalUriHandler.current

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)

        LaunchedEffect(event) {
            when (event) {
                is LoginEvent.OnAuthOpen -> {
                    uriHandler.openUri((event as LoginEvent.OnAuthOpen).url)
                    viewModel.onEvent(LoginEvent.FetchToken)
                }
                is LoginEvent.TokenInvalid -> accessToken = null
            }
        }

        LaunchedEffect(accessToken) {
            accessToken?.let { at ->
                if (state.user == null) {
                    val token = Token(accessToken = at)
                    viewModel.onEvent(LoginEvent.UpdateUser(token))
                }
            }
        }

        LaunchedEffect(state.user) {
            state.user?.let { user ->
                accessToken = user.token.accessToken
                viewModel.onEvent(LoginEvent.OnBeforeNavigate)

                val (ietfCode, resourceType) = initialPath?.let { path ->
                    val parts = path.replace("/", "").split("_")
                    if (parts.size == 2) {
                        parts[0] to parts[1]
                    } else {
                        null
                    }
                } ?: (null to null)

                if (ietfCode != null && resourceType != null) {
                    UrlManager.replaceAll(
                        listOf(
                            HomeScreen(user),
                            ReviewScreen(ietfCode, resourceType, user)
                        )
                    )
                } else {
                    navigator.replaceAll(HomeScreen(user))
                }
            }
        }

        Scaffold {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.progress) {
                    Text(stringResource(Res.string.login_progress))
                } else {
                    Button(
                        onClick = {
                            viewModel.onEvent(LoginEvent.Authorize)
                        }
                    ) {
                        Text(stringResource(Res.string.login))
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
        }
    }
}