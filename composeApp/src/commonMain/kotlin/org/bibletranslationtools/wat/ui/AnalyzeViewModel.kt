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
import kotlinx.serialization.json.Json
import org.bibletranslationtools.wat.asSource
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.ConsensusResult
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.ModelConsensus
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.SingletonWord
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.AiResponse
import org.bibletranslationtools.wat.domain.BatchRequest
import org.bibletranslationtools.wat.domain.BatchStatus
import org.bibletranslationtools.wat.domain.ModelResponse
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatAiApi
import org.bibletranslationtools.wat.formatWith
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.all_results_received
import wordanalysistool.composeapp.generated.resources.batch_deleted
import wordanalysistool.composeapp.generated.resources.batch_not_deleted
import wordanalysistool.composeapp.generated.resources.creating_batch
import wordanalysistool.composeapp.generated.resources.finding_singleton_words
import wordanalysistool.composeapp.generated.resources.no_model_selected
import wordanalysistool.composeapp.generated.resources.prompt_not_set
import wordanalysistool.composeapp.generated.resources.report_saved
import wordanalysistool.composeapp.generated.resources.token_invalid
import kotlin.math.min

private const val BATCH_REQUEST_DELAY = 1000L
private const val BATCH_REQUESTS_LIMIT = 10

data class Alert(
    val message: String,
    val onClosed: () -> Unit = {}
)

data class AnalyzeState(
    val batchProgress: Float = -1f,
    val singletons: List<SingletonWord> = emptyList(),
    val prompt: String? = null,
    val sorting: WordsSorting = WordsSorting.BY_ALPHABET,
    val models: List<String> = emptyList(),
    val alert: Alert? = null,
    val progress: Progress? = null
)

sealed class AnalyzeEvent {
    data object Idle : AnalyzeEvent()
    data object BatchWords : AnalyzeEvent()
    data object DeleteBatch : AnalyzeEvent()
    data class FindSingletons(val apostropheIsSeparator: Boolean) : AnalyzeEvent()
    data class UpdatePrompt(val value: String) : AnalyzeEvent()
    data class UpdateModels(val value: List<String>) : AnalyzeEvent()
    data class UpdateSorting(val value: WordsSorting) : AnalyzeEvent()
    data object WordsSorted : AnalyzeEvent()
    data object SaveReport : AnalyzeEvent()
    data object Logout : AnalyzeEvent()
}

enum class WordsSorting(val value: String) {
    BY_ALPHABET("By alphabet"),
    BY_ERROR("By misspelling"),
    BY_UNDEFINED("By undefined")
}

class AnalyzeViewModel(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: List<Verse>,
    private val user: User,
    private val watAiApi: WatAiApi
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
            is AnalyzeEvent.UpdatePrompt -> updatePrompt(event.value)
            is AnalyzeEvent.UpdateSorting -> updateSorting(event.value)
            is AnalyzeEvent.SaveReport -> saveReport()
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
                watAiApi.getBatch(
                    language.ietfCode,
                    resourceType,
                    user.token.accessToken
                ).onSuccess { batch ->
                    batch.details.output?.let { parseResponses(it) }
                    status = batch.details.status

                    val current = batch.details.progress.completed + batch.details.progress.failed
                    val total = batch.details.progress.total.toFloat()
                    updateBatchProgress(current / total)
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
                            println(it.description)
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

            if (_state.value.prompt?.trim().isNullOrEmpty()) {
                updateAlert(
                    Alert(getString(Res.string.prompt_not_set)) {
                        updateAlert(null)
                    }
                )
                return@launch
            }

            updateProgress(Progress(-1f, getString(Res.string.creating_batch)))

            val singletons = _state.value.singletons
                .filter { it.result == null }
                .take(BATCH_REQUESTS_LIMIT)

            val requests = singletons
                .map { singleton ->
                    BatchRequest(
                        id = singleton.word,
                        prompt = getPrompt(singleton.word, singleton.ref),
                        models = _state.value.models
                    )
                }

            if (requests.isEmpty()) {
                updateAlert(
                    Alert(getString(Res.string.all_results_received)) {
                        updateAlert(null)
                    }
                )
                updateProgress(null)
                return@launch
            }

            val json = buildJsonlFile(requests)
            val source = json.asSource()

            watAiApi.createBatch(
                language.ietfCode,
                resourceType,
                source,
                user.token.accessToken
            ).onSuccess {
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

                    else -> updateAlert(
                        Alert(it.description ?: "Error code: ${it.code}") {
                            updateAlert(null)
                        }
                    )
                }
            }

            updateProgress(null)
        }
    }

    private fun deleteBatch() {
        screenModelScope.launch {
            watAiApi.deleteBatch(language.ietfCode, resourceType, user.token.accessToken)
                .onSuccess { deleted ->
                    if (deleted) {
                        updateSingletons(
                            _state.value.singletons.map { it.copy(result = null) }
                        )
                        updateAlert(
                            Alert(getString(Res.string.batch_deleted)) {
                                updateAlert(null)
                            }
                        )
                    } else {
                        updateAlert(
                            Alert(getString(Res.string.batch_not_deleted)) {
                                updateAlert(null)
                            }
                        )
                    }
                }
                .onError {
                    updateAlert(
                        Alert(it.description ?: "") {
                            updateAlert(null)
                        }
                    )
                }
        }
    }

    private fun parseResponses(responses: List<AiResponse>) {
        val consensusMap: Map<String, ConsensusResult> = makeConsensus(responses)
        updateSingletons(
            _state.value.singletons.map { singleton ->
                consensusMap[singleton.word]?.let { answer ->
                    singleton.copy(result = answer)
                } ?: singleton
            }
        )
        sortWords(_state.value.sorting)
    }

    private fun saveReport() {
        screenModelScope.launch {
            val header = StringBuilder()
            header.append("word,book,chapter,verse,")
            _state.value.models.forEach {
                header.append(it)
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
                    builder.append(it.consensus.name)
                    builder.append(",")
                }

                builder.append(singleton.result?.consensus?.name)
                builder.toString()
            }

            val report = header.toString() + words

            FileKit.saveFile(
                baseName = "report",
                extension = "csv",
                bytes = report.encodeToByteArray()
            )

            updateAlert(
                Alert(getString(Res.string.report_saved)) {
                    updateAlert(null)
                }
            )
        }
    }

    private fun sortWords(sort: WordsSorting) {
        if (_state.value.singletons.isEmpty()) return

        screenModelScope.launch {
            when (sort) {
                WordsSorting.BY_ALPHABET -> {
                    updateSingletons(
                        _state.value.singletons.sortedBy { it.word.lowercase() }
                    )
                }

                WordsSorting.BY_ERROR -> {
                    updateSingletons(
                        _state.value.singletons.sortedByDescending {
                            it.result?.consensus == Consensus.MISSPELLING
                        }
                    )
                }

                WordsSorting.BY_UNDEFINED -> {
                    updateSingletons(
                        _state.value.singletons.sortedByDescending {
                            it.result?.consensus == Consensus.UNDEFINED
                        }
                    )
                }
            }

            _event.send(AnalyzeEvent.WordsSorted)
        }
    }

    private fun updateSorting(sort: WordsSorting) {
        _state.update {
            it.copy(sorting = sort)
        }
        sortWords(sort)
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

    private fun updatePrompt(prompt: String) {
        _state.update {
            it.copy(prompt = prompt)
        }
    }

    private fun getPrompt(word: String, verse: Verse): String {
        return _state.value.prompt!!.formatWith(
            mapOf<String, String>(
                "language" to language.angName,
                "book_name" to verse.bookName,
                "book_code" to verse.bookSlug,
                "chapter" to verse.chapter.toString(),
                "verse" to verse.number.toString(),
                "word" to word,
                "text" to verse.text
            )
        )
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

    private fun resetChannel() {
        screenModelScope.launch {
            _event.send(AnalyzeEvent.Idle)
        }
    }

    private fun makeConsensus(results: List<AiResponse>): Map<String, ConsensusResult> {
        val consensusMap = mutableMapOf<String, ConsensusResult>()

        results.forEach { response ->
            response.results?.let {
                consensusMap[response.id] = ConsensusResult(
                    models = it.map {
                        ModelConsensus(
                            model = it.model,
                            consensus = Consensus.of(it.result)
                        )
                    },
                    consensus = findWinner(it)
                )
            } ?: run {
                if (response.errored) {
                    println("Word '${response.id}' has errored with message: ${response.lastError}")
                }
            }
        }

        return consensusMap
    }

    private fun findWinner(results: List<ModelResponse>): Consensus {
        var misspell = 0
        var properName = 0
        var somethingElse = 0
        results.forEach { result ->
            // Limit long answers to 20 characters
            val limit = min(20, result.result.length)
            val answer = result.result.substring(0, limit).lowercase()

            when {
                answer.contains("proper name") -> properName++
                answer.contains("proper noun") -> properName++
                answer.contains("misspell") -> misspell++
                answer.contains("typo") -> misspell++
                answer.contains("something else") -> somethingElse++
            }
        }

        var max = maxOf(misspell, properName, somethingElse)
        var winners = 0
        var winnerName = Consensus.UNDEFINED

        if (misspell == max) {
            winners++
            winnerName = Consensus.MISSPELLING
        }

        if (properName == max) {
            winners++
            winnerName = Consensus.PROPER_NAME
        }

        if (somethingElse == max) {
            winners++
            winnerName = Consensus.SOMETHING_ELSE
        }

        val ratio = max / results.size.toFloat()
        if (ratio < 0.5f || winners > 1) {
            winnerName = Consensus.UNDEFINED
        }

        return winnerName
    }

    private fun buildJsonlFile(
        requests: List<BatchRequest>,
        json: Json = Json
    ): String = buildString {
        for (request in requests) {
            appendLine(json.encodeToString(request))
        }
    }
}