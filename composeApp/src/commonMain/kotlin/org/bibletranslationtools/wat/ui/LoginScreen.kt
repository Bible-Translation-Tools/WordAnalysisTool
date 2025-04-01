package org.bibletranslationtools.wat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.burnoo.compose.remembersetting.rememberStringSettingOrNull
import org.bibletranslationtools.wat.domain.Settings
import org.bibletranslationtools.wat.domain.Token
import org.bibletranslationtools.wat.ui.dialogs.AlertDialog
import org.jetbrains.compose.resources.stringResource
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.login
import wordanalysistool.composeapp.generated.resources.login_progress

class LoginScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<LoginViewModel>()
        val navigator = LocalNavigator.currentOrThrow

        val state by viewModel.state.collectAsStateWithLifecycle()
        val event by viewModel.event.collectAsStateWithLifecycle(AnalyzeEvent.Idle)

        val uriHandler = LocalUriHandler.current

        var accessToken by rememberStringSettingOrNull(Settings.ACCESS_TOKEN.name)
        var refreshToken by rememberStringSettingOrNull(Settings.REFRESH_TOKEN.name)

        LaunchedEffect(event) {
            when (event) {
                is LoginEvent.OnAuthOpen -> {
                    uriHandler.openUri((event as LoginEvent.OnAuthOpen).url)
                    viewModel.onEvent(LoginEvent.FetchToken)
                }
                is LoginEvent.TokenInvalid -> accessToken = null
            }
        }

        LaunchedEffect(accessToken, refreshToken) {
            accessToken?.let { at ->
                refreshToken?.let {
                    if (state.user == null) {
                        viewModel.onEvent(LoginEvent.FetchUser(at))
                    }
                }
            }
        }

        LaunchedEffect(state.token) {
            state.token?.let {
                accessToken = it.accessToken
                refreshToken = it.refreshToken
            }
        }

        LaunchedEffect(state.user) {
            state.user?.let {
                viewModel.onEvent(LoginEvent.OnBeforeNavigate)
                navigator.push(HomeScreen(it, Token(accessToken!!, refreshToken!!)))
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
            }

            state.alert?.let {
                AlertDialog(
                    message = it,
                    onDismiss = { viewModel.onEvent(LoginEvent.ClearAlert) }
                )
            }
        }
    }
}