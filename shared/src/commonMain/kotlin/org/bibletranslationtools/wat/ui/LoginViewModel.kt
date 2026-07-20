package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.data.ToastInfo
import org.bibletranslationtools.wat.data.ToastType
import org.bibletranslationtools.wat.domain.Token
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.token_invalid
import wordanalysistool.shared.generated.resources.unknown_error

data class LoginState(
    val user: User? = null,
    val toast: ToastInfo? = null,
    val progress: Boolean = false
)

sealed class LoginEvent {
    data object Idle : LoginEvent()
    data object Authorize : LoginEvent()
    data class OnAuthOpen(val url: String) : LoginEvent()
    data object FetchToken : LoginEvent()
    data class UpdateUser(val token: Token) : LoginEvent()
    data object ClearAlert : LoginEvent()
    data object OnBeforeNavigate : LoginEvent()
    data object TokenInvalid : LoginEvent()
}

class LoginViewModel(
    private val watApi: WatApi
) : ScreenModel {
    private var _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LoginState()
        )

    private val _event: Channel<LoginEvent> = Channel()
    val event = _event.receiveAsFlow()

    private var fetchJob by mutableStateOf<Job?>(null)

    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.Authorize -> authorize()
            is LoginEvent.FetchToken -> fetchToken()
            is LoginEvent.UpdateUser -> tokenToUser(event.token)
            is LoginEvent.OnBeforeNavigate -> onBeforeNavigate()
            is LoginEvent.ClearAlert -> updateToast(null)
            else -> resetChannel()
        }
    }

    private fun authorize() {
        screenModelScope.launch {
            watApi.getAuthUrl()
                .onSuccess {
                    _event.send(LoginEvent.OnAuthOpen(it))
                }
                .onError {
                    updateToast(
                        ToastInfo(
                            type = ToastType.Error,
                            message = it.description ?: getString(Res.string.unknown_error),
                            onClose = { updateToast(null) }
                        )
                    )
                }
        }
    }

    private fun fetchToken() {
        fetchJob?.cancel() // cancel previous job

        fetchJob = screenModelScope.launch {
            updateProgress(true)

            while (true) {
                watApi.getAuthToken()
                    .onSuccess {
                        tokenToUser(it)
                        fetchJob?.cancel()
                    }
                    .onError {
                        println(it.description)
                    }
                delay(1000)
            }
        }
    }

    private fun tokenToUser(token: Token) {
        screenModelScope.launch {
            updateProgress(true)

            watApi.verifyUser(token.accessToken)
                .onSuccess {
                    try {
                        updateUser(User.fromToken(token))
                    } catch (_: Exception) {
                        updateToast(
                            ToastInfo(
                                type = ToastType.Error,
                                message = getString(Res.string.token_invalid),
                                onClose = { updateToast(null) }
                            )
                        )
                        _event.send(LoginEvent.TokenInvalid)
                    }
                }
                .onError {
                    when (it.type) {
                        ErrorType.Unauthorized -> {
                            updateToast(
                                ToastInfo(
                                    type = ToastType.Error,
                                    message = getString(Res.string.token_invalid),
                                    onClose = { updateToast(null) }
                                )
                            )
                            _event.send(LoginEvent.TokenInvalid)
                        }
                        else -> updateToast(
                            ToastInfo(
                                type = ToastType.Error,
                                message = it.description ?: getString(Res.string.unknown_error),
                                onClose = { updateToast(null) }
                            )
                        )
                    }
                }

            updateProgress(false)
        }
    }

    private fun updateUser(user: User?) {
        _state.update {
            it.copy(user = user)
        }
    }

    private fun updateToast(toast: ToastInfo?) {
        _state.update {
            it.copy(toast = toast)
        }
    }

    private fun updateProgress(progress: Boolean) {
        _state.update {
            it.copy(progress = progress)
        }
    }

    private fun resetChannel() {
        screenModelScope.launch {
            _event.send(LoginEvent.Idle)
        }
    }

    private fun onBeforeNavigate() {
        fetchJob?.cancel()
        updateUser(null)
        updateToast(null)
        updateProgress(false)
    }
}