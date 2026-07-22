package org.bibletranslationtools.wat.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.Direction
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.ToastInfo
import org.bibletranslationtools.wat.data.ToastType
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.fetching_batches
import wordanalysistool.shared.generated.resources.fetching_heart_languages
import wordanalysistool.shared.generated.resources.fetching_resource_types
import wordanalysistool.shared.generated.resources.unknown_error

data class BatchItem(
    val id: String,
    val language: LanguageInfo,
    val resourceType: String,
    val username: String
)

data class HomeState(
    val toast: ToastInfo? = null,
    val progress: Progress? = null,
    val heartLanguages: List<LanguageInfo> = emptyList(),
    val resourceTypes: List<String> = emptyList(),
    val batches: List<BatchItem> = emptyList()
)

sealed class HomeEvent {
    data object Idle: HomeEvent()
    data class FetchResourceTypes(val ietfCode: String): HomeEvent()
}

class HomeViewModel(
    private val user: User,
    private val bielGraphQlApi: BielGraphQlApi,
    private val watApi: WatApi
) : ScreenModel {

    private var initialized = false

    private var _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state
        .onStart {
            if (!initialized) {
                initialized = true
                fetchHeartLanguages()
            }
        }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeState()
        )

    private val _event: Channel<HomeEvent> = Channel()

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.FetchResourceTypes -> fetchResourceTypes(event.ietfCode)
            else -> resetChannel()
        }
    }

    private fun fetchHeartLanguages() {
        screenModelScope.launch {
            updateProgress(
                Progress(
                    value = 0f,
                    message = getString(Res.string.fetching_heart_languages)
                )
            )
            updateHeartLanguages(bielGraphQlApi.getLanguages(gateway = false))
            updateProgress(null)

            fetchBatchesInProgress()
        }
    }

    private fun fetchResourceTypes(ietfCode: String) {
        screenModelScope.launch {
            updateProgress(Progress(0f, getString(Res.string.fetching_resource_types)))
            // TODO Remove debug code
            val resourceTypes = if (ietfCode in listOf("en","ru")) {
                listOf("ulb")
            } else {
                bielGraphQlApi.getUsfmForLanguage(ietfCode).keys.toList()
            }
            updateResourceTypes(resourceTypes)
            updateProgress(null)
        }
    }

    private suspend fun fetchBatchesInProgress() {
        updateProgress(Progress(
            0f,
            getString(Res.string.fetching_batches))
        )
        withContext(Dispatchers.Default) {
            watApi.getBatchesInProgress(user.token.accessToken)
                .onSuccess { batches ->
                    val batchItems = batches.map { batch ->
                        val language = _state.value.heartLanguages.find {
                            it.ietfCode == batch.ietfCode
                        } ?: LanguageInfo(
                            ietfCode = batch.ietfCode,
                            name = batch.ietfCode,
                            angName = batch.ietfCode,
                            direction = Direction.LTR
                        )
                        BatchItem(
                            id = batch.id,
                            language = language,
                            resourceType = batch.resourceType,
                            username = batch.creator.username
                        )
                    }
                    updateBatches(batchItems)
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
        updateProgress(null)
    }

    private fun updateHeartLanguages(heartLanguages: List<LanguageInfo>) {
        _state.update {
            it.copy(heartLanguages = heartLanguages)
        }
    }

    private fun updateResourceTypes(resourceTypes: List<String>) {
        _state.update {
            it.copy(resourceTypes = resourceTypes)
        }
    }

    private fun updateBatches(batches: List<BatchItem>) {
        _state.update {
            it.copy(batches = batches)
        }
    }

    private fun updateProgress(progress: Progress?) {
        _state.update {
            it.copy(progress = progress)
        }
    }

    private fun updateToast(toast: ToastInfo?) {
        _state.update {
            it.copy(toast = toast)
        }
    }

    private fun resetChannel() {
        screenModelScope.launch {
            _event.send(HomeEvent.Idle)
        }
    }
}