package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.ToastInfo
import org.bibletranslationtools.wat.data.ToastType
import org.bibletranslationtools.wat.domain.Batch
import org.bibletranslationtools.wat.domain.BatchRequest
import org.bibletranslationtools.wat.domain.BatchStatus
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.format
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.bibletranslationtools.wat.platform.saveFile
import org.bibletranslationtools.wat.ui.control.Status
import org.jetbrains.compose.resources.getString
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.batch_deleted
import wordanalysistool.shared.generated.resources.batch_not_deleted
import wordanalysistool.shared.generated.resources.batch_not_paused
import wordanalysistool.shared.generated.resources.batch_paused
import wordanalysistool.shared.generated.resources.creating_batch
import wordanalysistool.shared.generated.resources.deleting_batch
import wordanalysistool.shared.generated.resources.generating_report
import wordanalysistool.shared.generated.resources.invalid_batch_id
import wordanalysistool.shared.generated.resources.no_model_selected
import wordanalysistool.shared.generated.resources.pausing_batch
import wordanalysistool.shared.generated.resources.report_saved
import wordanalysistool.shared.generated.resources.reset_review_progress_success
import wordanalysistool.shared.generated.resources.resetting_review_progress
import wordanalysistool.shared.generated.resources.token_invalid
import wordanalysistool.shared.generated.resources.unknown_error
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val BATCH_REQUEST_DELAY = 10000L

data class AdminState(
    val batch: Batch? = null,
    val batchProgress: Float = -1f,
    val prompt: String? = null,
    val models: List<String> = emptyList(),
    val toast: ToastInfo? = null,
    val progress: Progress? = null,
    val status: Status? = null,
    val language: LanguageInfo? = null,
    val apostropheIsSeparator: Boolean = true,
    val refIetf: String? = null,
    val refResourceType: String? = null,
    val refLanguageName: String? = null,
    val refLanguages: List<LanguageInfo> = emptyList(),
    val refResourceTypes: List<String> = emptyList()
)

sealed class AdminEvent {
    data object Idle : AdminEvent()
    data object BatchWords : AdminEvent()
    data object PauseBatch: AdminEvent()
    data object DeleteBatch : AdminEvent()
    data object SaveReport : AdminEvent()
    data class ResetReview(val batchId: String) : AdminEvent()
    data object Logout : AdminEvent()
    data class UpdateModels(val value: List<String>) : AdminEvent()
    data class SetApostrophe(val value: Boolean) : AdminEvent()
    data class SetReference(
        val ietf: String?,
        val resourceType: String?
    ) : AdminEvent()
    data object FetchRefLanguages : AdminEvent()
    data class FetchRefResourceTypes(val ietfCode: String) : AdminEvent()
}

class AdminViewModel(
    private val ietfCode: String,
    private val resourceType: String,
    private val user: User,
    private val watApi: WatApi,
    private val bielGraphQlApi: BielGraphQlApi
) : ScreenModel {

    private var initialized = false

    private var _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state
        .onStart {
            if (!initialized) {
                initialized = true
                loadLanguage(ietfCode)
                fetchBatch(loop = false)
            }
        }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AdminState()
        )

    private val _event: Channel<AdminEvent> = Channel()
    val event = _event.receiveAsFlow()

    private var fetchJob by mutableStateOf<Job?>(null)

    fun onEvent(event: AdminEvent) {
        when (event) {
            is AdminEvent.SetApostrophe ->
                _state.update { it.copy(apostropheIsSeparator = event.value) }
            is AdminEvent.SetReference -> {
                _state.update {
                    it.copy(
                        refIetf = event.ietf,
                        refResourceType = event.resourceType
                    )
                }
                resolveRefLanguageName(event.ietf)
            }
            is AdminEvent.FetchRefLanguages -> fetchRefLanguages()
            is AdminEvent.FetchRefResourceTypes ->
                fetchRefResourceTypes(event.ietfCode)
            is AdminEvent.UpdateModels -> updateModels(event.value)
            is AdminEvent.BatchWords -> createBatch()
            is AdminEvent.PauseBatch -> pauseBatch()
            is AdminEvent.DeleteBatch -> deleteBatch()
            is AdminEvent.SaveReport -> saveReport()
            is AdminEvent.ResetReview -> resetReview(event.batchId)
            else -> resetChannel()
        }
    }

    private fun loadLanguage(ietfCode: String) {
        screenModelScope.launch {
            _state.update {
                it.copy(language = bielGraphQlApi.getLanguageInfo(ietfCode))
            }
        }
    }

    private fun fetchRefLanguages() {
        if (_state.value.refLanguages.isNotEmpty()) return
        screenModelScope.launch {
            val languages = bielGraphQlApi.getLanguages()
            _state.update { it.copy(refLanguages = languages) }
        }
    }

    private fun fetchRefResourceTypes(ietfCode: String) {
        screenModelScope.launch {
            val resourceTypes = bielGraphQlApi
                .getUsfmForLanguage(ietfCode)
                .keys
                .toList()
            _state.update { it.copy(refResourceTypes = resourceTypes) }
        }
    }

    // Resolve the reference ietf code to a display name (already-loaded list
    // first, else a lookup), so the field can show a name instead of a code.
    private fun resolveRefLanguageName(ietf: String?) {
        if (ietf.isNullOrBlank()) {
            _state.update { it.copy(refLanguageName = null) }
            return
        }
        screenModelScope.launch {
            val name = _state.value.refLanguages
                .find { it.ietfCode == ietf }?.name
                ?: bielGraphQlApi.getLanguageInfo(ietf)?.name
            _state.update { it.copy(refLanguageName = name) }
        }
    }


    private fun fetchBatch(loop: Boolean = true) {
        fetchJob?.cancel() // cancel previous job

        fetchJob = screenModelScope.launch {
            updateBatchProgress(0f)

            var status = BatchStatus.QUEUED
            val completionStatuses = listOf(
                BatchStatus.COMPLETE,
                BatchStatus.ERRORED,
                BatchStatus.TERMINATED,
                BatchStatus.UNKNOWN
            )

            while (status !in completionStatuses) {
                updateStatus("Fetching batch status...")

                watApi.getBatchStats(
                    ietfCode,
                    resourceType,
                    user.token.accessToken
                ).onSuccess { batch ->
                    status = batch.details.status

                    updateBatch(batch)

                    // Populate the reference from the server (unless the user
                    // has a pending local selection).
                    if (_state.value.refIetf.isNullOrBlank()) {
                        batch.reference?.let { ref ->
                            _state.update {
                                it.copy(
                                    refIetf = ref.ietf,
                                    refResourceType = ref.resourceType,
                                    refLanguageName = ref.name
                                )
                            }
                        }
                    }

                    val current = batch.details.progress.completed
                    val total = batch.details.progress.total
                    val progress = current / total.toFloat()

                    if (total > 0) {
                        updateBatchProgress(progress)
                        updateStatus(
                            "Current batch progress: ${(progress * 100).toInt()}"
                        )
                    }

                    batch.details.error?.let {
                        updateStatus(it)
                    }
                }.onError {
                    when (it.type) {
                        ErrorType.Unauthorized -> {
                            updateToast(
                                ToastInfo(
                                    type = ToastType.Error,
                                    message = getString(Res.string.token_invalid),
                                    onClose = {
                                        screenModelScope.launch {
                                            _event.send(AdminEvent.Logout)
                                            updateToast(null)
                                        }
                                    }
                                )
                            )
                        }
                        else -> {
                            updateStatus(it.description)
                            if (!loop) {
                                status = BatchStatus.ERRORED
                            }
                        }
                    }
                }

                if (!loop && status in completionStatuses) break

                delay(BATCH_REQUEST_DELAY)
            }

            updateBatchProgress(-1f)
            updateStatus("Batch results received")
            updateStatus("Idle")
        }
    }

    private fun createBatch() {
        screenModelScope.launch {
            if (_state.value.models.isEmpty()) {
                updateToast(
                    ToastInfo(
                        type = ToastType.Error,
                        message = getString(Res.string.no_model_selected),
                        onClose = { updateToast(null) }
                    )
                )
                return@launch
            }

            updateProgress(Progress(
                -1f,
                getString(Res.string.creating_batch))
            )

            updateStatus("Sending batch request...")

            // Singletons are now found on the worker during source ingestion;
            // the client only sends the models + tokenization option.
            val request = BatchRequest(
                models = _state.value.models,
                apostropheIsSeparator = _state.value.apostropheIsSeparator,
                refIetf = _state.value.refIetf?.ifBlank { null },
                refResourceType = _state.value.refResourceType?.ifBlank { null }
            )

            watApi.createBatch(
                ietfCode,
                resourceType,
                request,
                user.token.accessToken
            ).onSuccess {
                updateStatus("Batch request sent, waiting for result...")
                fetchBatch()
            }.onError {
                when (it.type) {
                    ErrorType.Unauthorized -> {
                        updateToast(
                            ToastInfo(
                                type = ToastType.Error,
                                message = getString(Res.string.token_invalid),
                                onClose = {
                                    screenModelScope.launch {
                                        _event.send(AdminEvent.Logout)
                                        updateToast(null)
                                    }
                                }
                            )
                        )
                    }

                    else -> {
                        updateStatus(it.description)
                        updateToast(
                            ToastInfo(
                                type = ToastType.Error,
                                message = it.description ?: "Error code: ${it.code}",
                                onClose = { updateToast(null) }
                            )
                        )
                    }
                }
            }

            updateProgress(null)
        }
    }

    private fun pauseBatch() {
        screenModelScope.launch {
            if (_state.value.batch == null) {
                updateToast(
                    ToastInfo(
                        type = ToastType.Error,
                        message = getString(Res.string.invalid_batch_id),
                        onClose = { updateToast(null) }
                    )
                )
                return@launch
            }

            updateStatus("Pausing batch results...")
            updateProgress(Progress(
                -1f,
                getString(Res.string.pausing_batch))
            )

            watApi.pauseBatch(
                _state.value.batch!!.id,
                user.token.accessToken
            )
                .onSuccess { cancelled ->
                    if (cancelled) {
                        updateStatus("Batch paused")
                        updateToast(
                            ToastInfo(
                                type = ToastType.Success,
                                message = getString(Res.string.batch_paused),
                                onClose = { updateToast(null) }
                            )
                        )
                        fetchJob?.cancel()
                        updateBatchProgress(-1f)
                    } else {
                        updateStatus("Could not pause batch.")
                        updateToast(
                            ToastInfo(
                                type = ToastType.Error,
                                message = getString(Res.string.batch_not_paused),
                                onClose = { updateToast(null) }
                            )
                        )
                    }
                }
                .onError {
                    updateStatus(it.description)
                    updateToast(
                        ToastInfo(
                            type = ToastType.Error,
                            message = it.description ?: getString(Res.string.unknown_error),
                            onClose = { updateToast(null) }
                        )
                    )
                }

            updateProgress(null)
        }
    }

    private fun deleteBatch() {
        screenModelScope.launch {
            if (_state.value.batch == null) {
                updateToast(
                    ToastInfo(
                        type = ToastType.Error,
                        message = getString(Res.string.invalid_batch_id),
                        onClose = { updateToast(null) }
                    )
                )
                return@launch
            }

            updateStatus("Deleting batch results...")
            updateProgress(Progress(
                -1f,
                getString(Res.string.deleting_batch))
            )

            watApi.deleteBatch(
                _state.value.batch!!.id,
                user.token.accessToken
            )
                .onSuccess { deleted ->
                    if (deleted) {
                        updateBatch(null)
                        updateStatus("Batch results deleted")
                        updateToast(
                            ToastInfo(
                                type = ToastType.Success,
                                message = getString(Res.string.batch_deleted),
                                onClose = { updateToast(null) }
                            )
                        )
                        fetchJob?.cancel()
                        updateBatchProgress(-1f)
                    } else {
                        updateStatus("Could not delete batch results.")
                        updateToast(
                            ToastInfo(
                                type = ToastType.Error,
                                message = getString(Res.string.batch_not_deleted),
                                onClose = { updateToast(null) }
                            )
                        )
                    }
                }
                .onError {
                    updateStatus(it.description)
                    updateToast(
                        ToastInfo(
                            type = ToastType.Error,
                            message = it.description ?: getString(Res.string.unknown_error),
                            onClose = { updateToast(null) }
                        )
                    )
                }

            updateProgress(null)
        }
    }

    private fun saveReport() {
        screenModelScope.launch {
            _state.update {
                it.copy(
                    progress = Progress(
                        -1f,
                        getString(Res.string.generating_report)
                    )
                )
            }

            withContext(Dispatchers.Default) {
                watApi.getBatchReport(
                    ietfCode,
                    resourceType,
                    user.token.accessToken
                ).onSuccess {
                    saveFile(
                        bytes = it,
                        filename = "report",
                        extension = "csv"
                    )
                }.onError {
                    println(it)
                }
            }

            _state.update {
                it.copy(
                    progress = null,
                    toast = ToastInfo(
                        type = ToastType.Success,
                        message = getString(Res.string.report_saved),
                        onClose = { updateToast(null) }
                    )
                )
            }
        }
    }

    private fun resetReview(batchId: String) {
        screenModelScope.launch {
            _state.update {
                it.copy(
                    progress = Progress(
                        -1f,
                        getString(Res.string.resetting_review_progress)
                    )
                )
            }

            val result = withContext(Dispatchers.Default) {
                var message = ""
                watApi.resetReviewProgress(
                    batchId,
                    user.token.accessToken
                ).onSuccess {
                    fetchBatch(loop = false)
                    message = getString(Res.string.reset_review_progress_success)
                }.onError {
                    message = it.description ?: "Unknown error"
                }
                message
            }

            _state.update {
                it.copy(
                    progress = null,
                    toast = ToastInfo(
                        type = ToastType.Success,
                        message = result,
                        onClose = { updateToast(null) }
                    )
                )
            }
        }
    }

    private fun updateBatch(batch: Batch?) {
        _state.update {
            it.copy(batch = batch)
        }
    }

    private fun updateModels(models: List<String>) {
        _state.update {
            it.copy(models = models)
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

    private fun updateBatchProgress(progress: Float) {
        _state.update {
            it.copy(batchProgress = progress)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun updateStatus(details: Any?) {
        val status = details?.let {
            val time = Clock.System.now().toLocalDateTime(
                TimeZone.currentSystemDefault()
            )
            Status(it, time.format())
        }
        delay(1)
        _state.update {
            it.copy(status = status)
        }
    }

    private fun resetChannel() {
        screenModelScope.launch {
            _event.send(AdminEvent.Idle)
        }
    }
}
