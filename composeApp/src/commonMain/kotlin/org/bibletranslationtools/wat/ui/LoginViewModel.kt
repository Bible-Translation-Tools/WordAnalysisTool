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
import org.bibletranslationtools.wat.domain.Token
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatAiApi
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.token_invalid
import kotlin.uuid.ExperimentalUuidApi

data class LoginState(
    val user: User? = null,
    val token: Token? = null,
    val alert: String? = null,
    val progress: Boolean = false
)

sealed class LoginEvent {
    data object Idle: LoginEvent()
    data object Authorize: LoginEvent()
    data class OnAuthOpen(val url: String): LoginEvent()
    data object FetchToken: LoginEvent()
    data class FetchUser(val accessToken: String): LoginEvent()
    data object ClearAlert: LoginEvent()
    data object OnBeforeNavigate: LoginEvent()
    data object TokenInvalid: LoginEvent()
}

class LoginViewModel(
    private val watAiApi: WatAiApi
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
            is LoginEvent.FetchUser -> fetchUser(event.accessToken)
            is LoginEvent.OnBeforeNavigate -> onBeforeNavigate()
            is LoginEvent.ClearAlert -> updateAlert(null)
            else -> resetChannel()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun authorize() {
        screenModelScope.launch {
            watAiApi.getAuthUrl()
                .onSuccess {
                    _event.send(LoginEvent.OnAuthOpen(it))
                }
                .onError {
                    updateAlert(it.description)
                }
        }
    }

    private fun fetchToken() {
        fetchJob?.cancel() // cancel previous job

        fetchJob = screenModelScope.launch {
            updateProgress(true)

            var token: Token? = null
            while (token == null) {
                watAiApi.getAuthToken()
                    .onSuccess {
                        updateToken(it)
                        fetchJob?.cancel()
                    }
                    .onError {
                        println(it.description)
                    }
                delay(1000)
            }
        }
    }

    private fun fetchUser(accessToken: String) {
        screenModelScope.launch {
            updateProgress(true)
            watAiApi.getAuthUser(accessToken)
                .onSuccess {
                    updateUser(it)
                }
                .onError {
                    when (it.type) {
                        ErrorType.Unauthorized -> updateAlert(getString(Res.string.token_invalid))
                        else -> updateAlert(it.description)
                    }
                    updateProgress(false)
                    _event.send(LoginEvent.TokenInvalid)
                }
        }
    }

    private fun updateUser(user: User?) {
        _state.update {
            it.copy(user = user)
        }
    }

    private fun updateToken(token: Token?) {
        _state.update {
            it.copy(token = token)
        }
    }

    private fun updateAlert(message: String?) {
        _state.update {
            it.copy(alert = message)
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
        updateToken(null)
        updateAlert(null)
        updateProgress(false)
    }
}