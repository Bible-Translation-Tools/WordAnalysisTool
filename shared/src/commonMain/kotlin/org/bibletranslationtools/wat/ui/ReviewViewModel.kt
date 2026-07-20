package org.bibletranslationtools.wat.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.ReviewWord
import org.bibletranslationtools.wat.data.ToastInfo
import org.bibletranslationtools.wat.data.ToastType
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.domain.WordRequest
import org.bibletranslationtools.wat.domain.WordsRequest
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.bibletranslationtools.wat.ui.control.SaveDirection
import org.jetbrains.compose.resources.getString
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.getting_batch
import wordanalysistool.shared.generated.resources.unflagged_marked_correct_message
import kotlin.math.ceil

private const val WORDS_PAGE_SIZE = 4

data class ReviewState(
    val isLoading: Boolean = false,
    val words: List<ReviewWord> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val completeProgress: Float = 0f,
    val toast: ToastInfo? = null,
    val batchId: String? = null,
    val progress: Progress? = null,
    val language: LanguageInfo? = null
)

sealed class ReviewEvent {
    data object Idle : ReviewEvent()
    data object Logout : ReviewEvent()
}

class ReviewViewModel(
    private val ietfCode: String,
    private val resourceType: String,
    private val user: User,
    private val batchId: String?,
    private val watApi: WatApi,
    private val bielGraphQlApi: BielGraphQlApi
) : ScreenModel {

    private var initialized = false

    private var _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state
        .onStart {
            if (!initialized) {
                initialized = true
                screenModelScope.launch {
                    loadLanguage(ietfCode)

                    batchId?.let { id ->
                        _state.update { it.copy(batchId = id) }
                    } ?: run {
                        fetchBatch()
                    }

                    loadPage(0)
                }
            }
        }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReviewState()
        )

    private val _event: Channel<ReviewEvent> = Channel()
    val event = _event.receiveAsFlow()

    private suspend fun fetchBatch() {
        _state.update {
            it.copy(
                progress = Progress(
                    -1f,
                    getString(Res.string.getting_batch)
                ),
                isLoading = true
            )
        }

        var errorMessage: String? = null
        watApi.getBatchStats(
            ietfCode = ietfCode,
            resourceType = resourceType,
            accessToken = user.token.accessToken
        ).onSuccess { batch ->
            _state.update { it.copy(batchId = batch.id) }
        }.onError { error ->
            errorMessage = error.description ?: "An error occurred."
        }

        if (errorMessage != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    progress = null
                )
            }
        }
    }

    private suspend fun loadPage(page: Int) {
        _state.update {
            it.copy(
                isLoading = true,
                progress = null
            )
        }

        withContext(Dispatchers.Default) {
            var errorMessage: String? = null
            watApi.getReviewPage(
                ietfCode = ietfCode,
                resourceType = resourceType,
                page = page,
                limit = WORDS_PAGE_SIZE,
                accessToken = user.token.accessToken
            ).onSuccess { batch ->
                if (batch.details.output.isNotEmpty()) {
                    val completed = batch.details.progress.reviewed
                    val total = batch.details.progress.total
                    val progress = completed / total.toFloat()
                    val currentPage = if (page > 0) {
                        page
                    } else {
                        if (total in 1..completed) {
                            ceil(total.toFloat() / WORDS_PAGE_SIZE).toInt()
                        } else {
                            (completed / WORDS_PAGE_SIZE) + 1
                        }
                    }
                    val words = batch.details.output.map { word ->
                        val parts = word.ref.split(":")
                        ReviewWord(
                            word = word.word,
                            ref = Verse(
                                book = parts.getOrElse(0) { "" },
                                chapter = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0,
                                verse = parts.getOrElse(2) { "" },
                                text = word.text
                            ),
                            correct = word.correct
                        )
                    }

                    _state.update {
                        it.copy(
                            words = words,
                            currentPage = currentPage,
                            totalPages = ceil(total.toFloat() / WORDS_PAGE_SIZE).toInt(),
                            completeProgress = progress,
                            isLoading = false
                        )
                    }
                } else {
                    errorMessage = "No words found."
                }
            }.onError { error ->
                if (error.type == ErrorType.Unauthorized) {
                    _event.send(ReviewEvent.Logout)
                } else {
                    errorMessage = error.description ?: "An error occurred"
                }
            }

            if (errorMessage != null) {
                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        toast = ToastInfo(
                            type = ToastType.Error,
                            message = errorMessage,
                            onClose = { _state.update { it.copy(toast = null) } }
                        )
                    )
                }
            }
        }
    }

    fun onFlagClicked(word: String) {
        val updatedPagedWords = _state.value.words.map { singleton ->
            if (singleton.word == word) {
                val newCorrectState = if (singleton.correct == false) null else false
                singleton.copy(correct = newCorrectState)
            } else singleton
        }
        _state.update { it.copy(words = updatedPagedWords) }
    }

    private suspend fun saveCurrentPage(andThen: suspend () -> Unit) {
        _state.update { it.copy(isLoading = true) }

        withContext(Dispatchers.Default) {
            watApi.updateWordsCorrect(
                request = WordsRequest(
                    batchId = _state.value.batchId!!,
                    words = _state.value.words.map {
                        WordRequest(it.word, it.correct ?: true)
                    }
                ),
                accessToken = user.token.accessToken
            ).onSuccess {
                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        toast = ToastInfo(
                            type = ToastType.Info,
                            message = getString(Res.string.unflagged_marked_correct_message),
                            onClose = { _state.update { it.copy(toast = null) } }
                        )
                    )
                }
                andThen()
            }.onError { error ->
                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        toast = ToastInfo(
                            type = ToastType.Error,
                            message = error.description ?: "An error occurred",
                            onClose = { _state.update { it.copy(toast = null) } }
                        )
                    )
                }
            }
        }
    }

    fun onSave(direction: SaveDirection) {
        screenModelScope.launch {
            val currentPage = _state.value.currentPage
            val totalPages = _state.value.totalPages
            saveCurrentPage {
                when {
                    direction == SaveDirection.NEXT && currentPage < totalPages -> {
                        loadPage(currentPage + 1)
                    }
                    direction == SaveDirection.PREV && currentPage > 1 -> {
                        loadPage(currentPage - 1)
                    }
                    else -> loadPage(currentPage)
                }
            }
            resetChannel()
        }
    }

    private fun loadLanguage(ietfCode: String) {
        screenModelScope.launch {
            _state.update {
                it.copy(language = bielGraphQlApi.getLanguageInfo(ietfCode))
            }
        }
    }

    private fun resetChannel() {
        screenModelScope.launch {
            _event.send(ReviewEvent.Idle)
        }
    }
}
