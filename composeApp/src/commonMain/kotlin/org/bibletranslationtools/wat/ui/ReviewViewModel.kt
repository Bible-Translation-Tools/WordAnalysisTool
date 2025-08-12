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
import org.bibletranslationtools.wat.data.Alert
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.ReviewWord
import org.bibletranslationtools.wat.data.VerseRef
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.domain.WordRequest
import org.bibletranslationtools.wat.domain.WordsRequest
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import kotlin.math.ceil

private const val WORDS_PAGE_SIZE = 5

data class ReviewState(
    val isLoading: Boolean = true,
    val words: List<ReviewWord> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val completeProgress: Float = 0f,
    val alert: Alert? = null,
    val batchId: String? = null,
)

sealed class ReviewEvent {
    data object Idle : ReviewEvent()
    data object Logout : ReviewEvent()
    data object Saved : ReviewEvent()
}

class ReviewViewModel(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: VerseRef,
    private val user: User,
    private val batchId: String?,
    private val watApi: WatApi
) : ScreenModel {

    private var _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state
        .onStart {
            screenModelScope.launch {
                batchId?.let { id ->
                    _state.update { it.copy(batchId = id) }
                    loadPage(0)
                } ?: run {
                    fetchBatch {
                        loadPage(0)
                    }
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

    private suspend fun fetchBatch(andThen: suspend () -> Unit) {
        var errorMessage: String? = null
        watApi.getBatchStats(
            ietfCode = language.ietfCode,
            resourceType = resourceType,
            accessToken = user.token.accessToken
        ).onSuccess { batch ->
            if (batch.details.output.isEmpty()) {
                errorMessage = "No words found."
            }
        }.onError { error ->
            errorMessage = error.description ?: "An error occurred."
        }

        if (errorMessage != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    alert = Alert(errorMessage) {
                        _state.update { state -> state.copy(alert = null) }
                    }
                )
            }

        } else andThen()
    }

    private suspend fun loadPage(page: Int) {
        _state.update { it.copy(isLoading = true, alert = null) }

        withContext(Dispatchers.Default) {
            var errorMessage: String? = null
            watApi.getReviewPage(
                ietfCode = language.ietfCode,
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
                        if (completed >= total && total > 0) {
                            ceil(total.toFloat() / WORDS_PAGE_SIZE).toInt()
                        } else {
                            (completed / WORDS_PAGE_SIZE) + 1
                        }
                    }
                    val words = batch.details.output.map { word ->
                        ReviewWord(
                            word = word.word,
                            ref = verses[word.ref]!!,
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
                _state.update {
                    it.copy(
                        isLoading = false,
                        alert = Alert(errorMessage) {
                            _state.update { state -> state.copy(alert = null) }
                        }
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

        val wordsToUpdate = _state.value.words.map { word ->
            if (word.correct == null) {
                word.copy(correct = true)
            } else {
                word
            }
        }

        withContext(Dispatchers.Default) {
            watApi.updateWordsCorrect(
                request = WordsRequest(
                    batchId = _state.value.batchId!!,
                    words = wordsToUpdate.map {
                        WordRequest(it.word, it.correct)
                    }
                ),
                accessToken = user.token.accessToken
            ).onSuccess {
                _state.update { it.copy(isLoading = false) }
                _event.send(ReviewEvent.Saved)
                andThen()
            }.onError { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        alert = Alert(error.description ?: "An error occurred") {
                            _state.update { state -> state.copy(alert = null) }
                        }
                    )
                }
            }
        }
    }

    fun onPageSelected(page: Int) {
        screenModelScope.launch {
            val currentState = _state.value
            if (page > 0 && page <= currentState.totalPages && page != currentState.currentPage) {
                loadPage(page)
            }
        }
    }

    fun onSaveAndNext() {
        screenModelScope.launch {
            val currentPage = _state.value.currentPage
            val totalPages = _state.value.totalPages
            saveCurrentPage {
                if (currentPage < totalPages) {
                    loadPage(currentPage + 1)
                } else {
                    loadPage(currentPage)
                }
            }
        }
    }
}
