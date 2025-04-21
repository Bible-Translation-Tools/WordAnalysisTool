package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.Alert
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.ConsensusResult
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.ModelStatus
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.SingletonWord
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.Batch
import org.bibletranslationtools.wat.domain.BatchRequest
import org.bibletranslationtools.wat.domain.BatchStatus
import org.bibletranslationtools.wat.domain.MODELS_SIZE
import org.bibletranslationtools.wat.domain.ModelResponse
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.domain.WordRequest
import org.bibletranslationtools.wat.domain.WordResponse
import org.bibletranslationtools.wat.domain.WordStatus
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.bibletranslationtools.wat.ui.AnalyzeEvent.RefreshSelectedWord
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.all_results_received
import wordanalysistool.composeapp.generated.resources.batch_deleted
import wordanalysistool.composeapp.generated.resources.batch_not_deleted
import wordanalysistool.composeapp.generated.resources.creating_batch
import wordanalysistool.composeapp.generated.resources.deleting_batch
import wordanalysistool.composeapp.generated.resources.finding_singleton_words
import wordanalysistool.composeapp.generated.resources.invalid_batch_id
import wordanalysistool.composeapp.generated.resources.no_model_selected
import wordanalysistool.composeapp.generated.resources.report_saved
import wordanalysistool.composeapp.generated.resources.token_invalid
import wordanalysistool.composeapp.generated.resources.updating_word
import wordanalysistool.composeapp.generated.resources.wrong_model_selected

private const val BATCH_REQUEST_DELAY = 3000L
private const val BATCH_REQUESTS_LIMIT = 12

data class AnalyzeState(
    val batch: Batch? = null,
    val batchProgress: Float = -1f,
    val singletons: List<SingletonWord> = emptyList(),
    val prompt: String? = null,
    val sorting: WordsSorting = WordsSorting.ALPHABET,
    val models: List<String> = emptyList(),
    val alert: Alert? = null,
    val progress: Progress? = null,
    val status: String? = null
)

sealed class AnalyzeEvent {
    data object Idle : AnalyzeEvent()
    data object BatchWords : AnalyzeEvent()
    data object DeleteBatch : AnalyzeEvent()
    data object WordsSorted : AnalyzeEvent()
    data object SaveReport : AnalyzeEvent()
    data object RefreshSelectedWord : AnalyzeEvent()
    data object Logout : AnalyzeEvent()
    data class UpdateModels(val value: List<String>) : AnalyzeEvent()
    data class FindSingletons(val apostropheIsSeparator: Boolean) : AnalyzeEvent()
    data class UpdateCorrect(val word: String, val correct: Boolean): AnalyzeEvent()
    data class UpdateSelectedWord(val value: Boolean?): AnalyzeEvent()
}

enum class WordsSorting {
    ALPHABET,
    ALPHABET_DESC,
    NAME,
    LIKELY_CORRECT,
    LIKELY_INCORRECT,
    NEEDS_REVIEW,
    REVIEWED
}

class AnalyzeViewModel(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: List<Verse>,
    private val user: User,
    private val watApi: WatApi
) : ScreenModel {

    private var _state = MutableStateFlow(AnalyzeState())
    val state: StateFlow<AnalyzeState> = _state
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyzeState()
        )

    private val _event: Channel<AnalyzeEvent> = Channel()
    val event = _event.receiveAsFlow()

    private var fetchJob by mutableStateOf<Job?>(null)

    private val apostropheRegex = "[\\p{L}'’]+(?<!['’])".toRegex()
    private val nonApostropheRegex = "\\p{L}+".toRegex()

    fun onEvent(event: AnalyzeEvent) {
        when (event) {
            is AnalyzeEvent.FindSingletons -> findSingletonWords(event.apostropheIsSeparator)
            is AnalyzeEvent.UpdateModels -> updateModels(event.value)
            is AnalyzeEvent.BatchWords -> createBatch()
            is AnalyzeEvent.DeleteBatch -> deleteBatch()
            is AnalyzeEvent.SaveReport -> saveReport()
            is AnalyzeEvent.UpdateCorrect -> updateWordCorrect(event.word, event.correct)
            else -> resetChannel()
        }
    }

    private fun findSingletonWords(apostropheIsSeparator: Boolean) {
        screenModelScope.launch {
            updateProgress(
                Progress(0f, getString(Res.string.finding_singleton_words))
            )

            val totalVerses = verses.size
            val tempMap = mutableMapOf<String, Pair<Int, Verse>>()
            val wordsRegex = if (apostropheIsSeparator) {
                nonApostropheRegex
            } else apostropheRegex

            withContext(Dispatchers.Default) {
                verses.forEachIndexed { index, verse ->
                    val words = wordsRegex.findAll(verse.text).map { it.value }

                    words.forEach { word ->
                        if (word.trim().isEmpty()) return@forEach

                        val w = tempMap.getOrElse(word) { 0 to verse }
                        tempMap[word] = w.copy(first = w.first + 1)
                    }

                    updateProgress(
                        Progress(
                            (index + 1) / totalVerses.toFloat(),
                            getString(Res.string.finding_singleton_words)
                        )
                    )
                }
            }

            updateSingletons(
                tempMap.entries
                    .filter { it.value.first == 1 }
                    .map { SingletonWord(it.key, it.value.second) }
                    .sortedBy { it.word.lowercase() }
            )

            fetchBatch(loop = false)

            updateProgress(null)
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

                watApi.getBatch(
                    language.ietfCode,
                    resourceType,
                    user.token.accessToken
                ).onSuccess { batch ->
                    parseResponses(batch.details.output)
                    status = batch.details.status

                    updateBatch(batch)

                    val current = batch.details.progress.completed
                    val total = batch.details.progress.total.toFloat()

                    if (total > 0) {
                        updateBatchProgress(current / total)
                        updateStatus(
                            "Current batch progress: ${((current / total) * 100).toInt()}"
                        )
                    }

                    batch.details.error?.let(::updateStatus)
                }.onError {
                    when (it.type) {
                        ErrorType.Unauthorized -> {
                            updateAlert(
                                Alert(getString(Res.string.token_invalid)) {
                                    screenModelScope.launch {
                                        _event.send(AnalyzeEvent.Logout)
                                        updateAlert(null)
                                    }
                                }
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

            _event.send(RefreshSelectedWord)

            updateBatchProgress(-1f)
            updateStatus("Batch results received")
            updateStatus("Idle")
        }
    }

    private fun createBatch() {
        screenModelScope.launch {
            if (_state.value.models.isEmpty()) {
                updateAlert(
                    Alert(getString(Res.string.no_model_selected)) {
                        updateAlert(null)
                    }
                )
                return@launch
            }

            if (_state.value.models.size != MODELS_SIZE) {
                updateAlert(
                    Alert(getString(Res.string.wrong_model_selected, MODELS_SIZE)) {
                        updateAlert(null)
                    }
                )
                return@launch
            }

            updateProgress(Progress(-1f, getString(Res.string.creating_batch)))

            val singletons = _state.value.singletons
                .filter { it.result == null }
                .take(BATCH_REQUESTS_LIMIT)

            if (singletons.isEmpty()) {
                updateAlert(
                    Alert(getString(Res.string.all_results_received)) {
                        updateAlert(null)
                    }
                )
                updateProgress(null)
                return@launch
            }

            val request = BatchRequest(
                language = language.angName,
                words = singletons.map { it.word },
                models = _state.value.models
            )

            updateStatus("Sending batch request...")

            watApi.createBatch(
                language.ietfCode,
                resourceType,
                request,
                user.token.accessToken
            ).onSuccess {
                updateStatus("Batch request sent, waiting for result...")
                fetchBatch()
            }.onError {
                when (it.type) {
                    ErrorType.Unauthorized -> {
                        updateAlert(
                            Alert(getString(Res.string.token_invalid)) {
                                screenModelScope.launch {
                                    _event.send(AnalyzeEvent.Logout)
                                    updateAlert(null)
                                }
                            }
                        )
                    }

                    else -> {
                        updateStatus(it.description)
                        updateAlert(
                            Alert(it.description ?: "Error code: ${it.code}") {
                                updateAlert(null)
                            }
                        )
                    }
                }
            }

            updateProgress(null)
        }
    }

    private fun deleteBatch() {
        screenModelScope.launch {
            if (_state.value.batch == null) {
                updateAlert(
                    Alert(getString(Res.string.invalid_batch_id)) {
                        updateAlert(null)
                    }
                )
                return@launch
            }

            updateStatus("Deleting batch results...")
            updateProgress(Progress(-1f, getString(Res.string.deleting_batch)))

            watApi.deleteBatch(_state.value.batch!!.id, user.token.accessToken)
                .onSuccess { deleted ->
                    if (deleted) {
                        updateBatch(null)
                        updateStatus("Batch results deleted")
                        updateSingletons(
                            _state.value.singletons.map {
                                it.copy(result = null, correct = null)
                            }
                        )
                        updateAlert(
                            Alert(getString(Res.string.batch_deleted)) {
                                updateAlert(null)
                            }
                        )
                    } else {
                        updateStatus("Could not delete batch results.")
                        updateAlert(
                            Alert(getString(Res.string.batch_not_deleted)) {
                                updateAlert(null)
                            }
                        )
                    }
                }
                .onError {
                    updateStatus(it.description)
                    updateAlert(
                        Alert(it.description ?: "") {
                            updateAlert(null)
                        }
                    )
                }

            updateProgress(null)
        }
    }

    private fun updateWordCorrect(word: String, correct: Boolean) {
        screenModelScope.launch {
            if (_state.value.batch == null) {
                updateAlert(
                    Alert(getString(Res.string.invalid_batch_id)) {
                        updateAlert(null)
                    }
                )
                return@launch
            }

            updateProgress(Progress(-1f, getString(Res.string.updating_word)))

            val request = WordRequest(
                _state.value.batch!!.id,
                word,
                correct
            )
            watApi.updateWordCorrect(request, user.token.accessToken)
                .onSuccess {
                    updateSingletons(
                        _state.value.singletons.map { singleton ->
                            if (singleton.word == request.word) {
                                singleton.copy(correct = request.correct)
                            } else {
                                singleton
                            }
                        }
                    )
                    _event.send(AnalyzeEvent.UpdateSelectedWord(request.correct))
                }
                .onError {
                    updateStatus(it.description)
                    updateAlert(
                        Alert(it.description ?: "") {
                            updateAlert(null)
                        }
                    )
                }

            updateProgress(null)
        }
    }

    private fun parseResponses(responses: List<WordResponse>) {
        val consensusMap: Map<String, Pair<Boolean?, ConsensusResult>> = makeConsensus(responses)
        updateSingletons(
            _state.value.singletons.map { singleton ->
                consensusMap[singleton.word]?.let { answer ->
                    singleton.copy(correct = answer.first, result = answer.second)
                } ?: singleton
            }
        )
    }

    private fun saveReport() {
        screenModelScope.launch {
            val header = StringBuilder()
            header.append("word,book,chapter,verse,")
            _state.value.models.forEachIndexed { i, model ->
                header.append("model${i+1}")
                header.append(",")
            }
            header.append("consensus\n")

            val words = _state.value.singletons.joinToString("\n") { singleton ->
                val builder = StringBuilder()
                builder.append(singleton.word)
                builder.append(",")
                builder.append(singleton.ref.bookName)
                builder.append(" (${singleton.ref.bookSlug})")
                builder.append(",")
                builder.append(singleton.ref.chapter)
                builder.append(",")
                builder.append(singleton.ref.number)
                builder.append(",")

                singleton.result?.models?.forEach {
                    builder.append("\"")
                    builder.append(it.model)
                    builder.append("\n")
                    builder.append(it.status.name)
                    builder.append("\"")
                    builder.append(",")
                }

                builder.append(singleton.result?.consensus?.name)
                builder.toString()
            }

            val report = header.toString() + words

            val saved = FileKit.saveFile(
                baseName = "report_${language.ietfCode}_${resourceType}",
                extension = "csv",
                bytes = report.encodeToByteArray()
            )

            if (saved != null) {
                updateAlert(
                    Alert(getString(Res.string.report_saved)) {
                        updateAlert(null)
                    }
                )
            }
        }
    }

    private fun updateBatch(batch: Batch?) {
        _state.update {
            it.copy(batch = batch)
        }
    }

    private fun updateSingletons(words: List<SingletonWord>) {
        _state.update {
            it.copy(singletons = words)
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

    private fun updateAlert(alert: Alert?) {
        _state.update {
            it.copy(alert = alert)
        }
    }

    private fun updateBatchProgress(progress: Float) {
        _state.update {
            it.copy(batchProgress = progress)
        }
    }

    private fun updateStatus(status: String?) {
        _state.update {
            it.copy(status = status)
        }
    }

    private fun resetChannel() {
        screenModelScope.launch {
            _event.send(AnalyzeEvent.Idle)
        }
    }

    private fun makeConsensus(
        responses: List<WordResponse>
    ): Map<String, Pair<Boolean?, ConsensusResult>> {
        val consensusMap = mutableMapOf<String, Pair<Boolean?, ConsensusResult>>()

        responses.forEach { response ->
            val hasUnchecked = response.results.any { it.status == WordStatus.UNCHECKED }

            if (!hasUnchecked) {
                consensusMap[response.word] = response.correct to ConsensusResult(
                    models = response.results.map {
                        ModelStatus(
                            model = it.model,
                            status = it.status
                        )
                    },
                    consensus = findWinner(response.results)
                )
            }
        }

        return consensusMap
    }

    private fun findWinner(results: List<ModelResponse>): Consensus {
        var incorrect = 0
        var correct = 0
        var name = 0

        results.forEach { result ->
            when (result.status) {
                WordStatus.CORRECT -> correct++
                WordStatus.INCORRECT -> incorrect++
                WordStatus.NAME -> name++
                else -> Unit
            }
        }

        return when {
            correct == results.size -> Consensus.LIKELY_CORRECT
            incorrect == results.size -> Consensus.LIKELY_INCORRECT
            name == results.size -> Consensus.NAME
            else -> Consensus.NEEDS_REVIEW
        }
    }
}
