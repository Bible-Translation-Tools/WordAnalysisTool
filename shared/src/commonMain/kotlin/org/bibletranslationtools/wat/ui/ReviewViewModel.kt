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
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.getting_batch

data class ReviewState(
    val isLoading: Boolean = false,
    val words: List<ReviewWord> = emptyList(),
    val currentIndex: Int = 0,
    val savingWord: String? = null,
    val toast: ToastInfo? = null,
    val batchId: String? = null,
    val progress: Progress? = null,
    val language: LanguageInfo? = null
) {
    val total: Int get() = words.size
    val reviewedCount: Int get() = words.count { it.correct != null }
    val completeProgress: Float get() = if (total == 0) {
        0f
    } else reviewedCount / total.toFloat()

    val currentWord: ReviewWord? get() = words.getOrNull(currentIndex)

    /**
     * The furthest card the user has unlocked: the first word still awaiting
     * a review, or the last word once everything has been reviewed.
     */
    val frontierIndex: Int get() = words
        .indexOfFirst { it.correct == null }
        .let { if (it == -1) total - 1 else it }

    val canGoPrev: Boolean get() = currentIndex > 0
    val canGoNext: Boolean get() = currentWord?.correct != null && currentIndex < total - 1
    val isComplete: Boolean get() = total > 0 && reviewedCount == total
}

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

                    loadWords()
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

    private suspend fun loadWords() {
        _state.update {
            it.copy(
                isLoading = true,
                progress = null
            )
        }

        withContext(Dispatchers.Default) {
            var errorMessage: String? = null
            watApi.getReviewWords(
                ietfCode = ietfCode,
                resourceType = resourceType,
                accessToken = user.token.accessToken
            ).onSuccess { batch ->
                if (batch.details.output.isNotEmpty()) {
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
                            isLoading = false
                        )
                    }
                    _state.update { it.copy(currentIndex = it.frontierIndex) }
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

    /** Records the review of the current word and saves it right away. */
    fun onVote(correct: Boolean) {
        val word = _state.value.currentWord ?: return
        if (word.correct == correct || _state.value.savingWord != null) return

        val previous = word.correct
        setCorrect(word.word, correct)
        _state.update { it.copy(savingWord = word.word) }

        screenModelScope.launch {
            withContext(Dispatchers.Default) {
                watApi.reviewWord(
                    batchId = _state.value.batchId!!,
                    word = word.word,
                    correct = correct,
                    accessToken = user.token.accessToken
                ).onSuccess {
                    _state.update { it.copy(savingWord = null) }
                }.onError { error ->
                    setCorrect(word.word, previous)
                    _state.update { state ->
                        state.copy(
                            savingWord = null,
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
    }

    private fun setCorrect(word: String, correct: Boolean?) {
        _state.update { state ->
            state.copy(
                words = state.words.map {
                    if (it.word == word) it.copy(correct = correct) else it
                }
            )
        }
    }

    /** Moves to [index], never past the last unlocked (reviewed) card. */
    fun goTo(index: Int) {
        _state.update { state ->
            val target = index.coerceIn(0, maxOf(0, state.frontierIndex))
            state.copy(currentIndex = target)
        }
    }

    fun goNext() = goTo(_state.value.currentIndex + 1)

    fun goPrev() = goTo(_state.value.currentIndex - 1)

    fun goFirst() = goTo(0)

    fun goLast() = goTo(_state.value.frontierIndex)

    private fun loadLanguage(ietfCode: String) {
        screenModelScope.launch {
            _state.update {
                it.copy(language = bielGraphQlApi.getLanguageInfo(ietfCode))
            }
        }
    }
}
