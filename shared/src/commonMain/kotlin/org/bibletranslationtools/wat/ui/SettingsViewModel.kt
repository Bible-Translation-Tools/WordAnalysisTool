package org.bibletranslationtools.wat.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.domain.UpdateLanguages
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.http.ApiResult
import org.bibletranslationtools.wat.http.NetworkError
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.languages_import_failed
import wordanalysistool.shared.generated.resources.languages_imported
import wordanalysistool.shared.generated.resources.updating_languages

data class SettingsState(
    val progress: Progress? = null,
    val alert: String? = null
)

sealed class SettingsEvent {
    data object Idle : SettingsEvent()
    data object DownloadLanguages : SettingsEvent()
    data class ImportLanguages(val file: PlatformFile) : SettingsEvent()
    data object DismissAlert : SettingsEvent()
}

class SettingsViewModel(
    private val user: User,
    private val updateLanguages: UpdateLanguages
) : ScreenModel {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.DownloadLanguages -> runUpdate {
                updateLanguages.fromUrl(user.token.accessToken)
            }
            is SettingsEvent.ImportLanguages -> runUpdate {
                updateLanguages.fromFile(event.file, user.token.accessToken)
            }
            is SettingsEvent.DismissAlert -> updateAlert(null)
            is SettingsEvent.Idle -> Unit
        }
    }

    private fun runUpdate(block: suspend () -> ApiResult<Int, NetworkError>) {
        screenModelScope.launch(Dispatchers.Default) {
            updateProgress(Progress(-1f, getString(Res.string.updating_languages)))

            block()
                .onSuccess { count ->
                    updateAlert(getString(Res.string.languages_imported, count))
                }
                .onError { error ->
                    updateAlert(
                        getString(
                            Res.string.languages_import_failed,
                            error.description ?: ""
                        )
                    )
                }

            updateProgress(null)
        }
    }

    private fun updateProgress(progress: Progress?) {
        _state.update { it.copy(progress = progress) }
    }

    private fun updateAlert(alert: String?) {
        _state.update { it.copy(alert = alert) }
    }
}
