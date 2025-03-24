package org.bibletranslationtools.wat.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.Dispatchers
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.ConsensusResult
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.ModelConsensus
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.SingletonWord
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.data.asSource
import org.bibletranslationtools.wat.data.sortedByKeyWith
import org.bibletranslationtools.wat.domain.AiResponse
import org.bibletranslationtools.wat.domain.Batch
import org.bibletranslationtools.wat.domain.BatchRequest
import org.bibletranslationtools.wat.domain.BatchStatus
import org.bibletranslationtools.wat.domain.ChatRequest
import org.bibletranslationtools.wat.domain.WatAiApi
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.asking_ai
import wordanalysistool.composeapp.generated.resources.finding_singleton_words
import wordanalysistool.composeapp.generated.resources.no_model_selected
import wordanalysistool.composeapp.generated.resources.report_saved
import kotlin.math.min

private const val BATCH_REQUEST_DELAY = 1000L

data class AnalyzeState(
    val batchId: String? = null,
    val batchProgress: Float = -1f,
    val singletons: Map<String, SingletonWord> = emptyMap(),
    val prompt: String? = null,
    val consensus: ConsensusResult? = null,
    val aiResponses: Map<String, String?> = emptyMap(),
    val models: List<String> = emptyList(),
    val alert: String? = null,
    val progress: Progress? = null
)

sealed class AnalyzeEvent {
    data object Idle: AnalyzeEvent()
    data object ClearResponse: AnalyzeEvent()
    data object ClearAlert: AnalyzeEvent()
    data class Chat(val word: String): AnalyzeEvent()
    data class FetchBatch(val batchId: String): AnalyzeEvent()
    data object CreateBatch: AnalyzeEvent()
    data class BatchCreated(val value: String): AnalyzeEvent()
    data object ReadyToCreateBatch: AnalyzeEvent()
    data class FindSingletons(val apostropheIsSeparator: Boolean): AnalyzeEvent()

    sealed class UpdatePrompt: AnalyzeEvent() {
        data class FromString(val value: String): UpdatePrompt()
        data class FromVerse(val word: String, val verse: Verse): UpdatePrompt()
    }

    data class UpdateBatchId(val value: String?): AnalyzeEvent()
    data class UpdateModels(val value: List<String>): AnalyzeEvent()
    data object SaveReport: AnalyzeEvent()
}

class AnalyzeViewModel(
    private val language: LanguageInfo,
    private val verses: List<Verse>,
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

    private val apostropheRegex = "[\\p{L}'’]+(?<!['’])".toRegex()
    private val nonApostropheRegex = "\\p{L}+".toRegex()

    fun onEvent(event: AnalyzeEvent) {
        when (event) {
            is AnalyzeEvent.FindSingletons -> findSingletonWords(event.apostropheIsSeparator)
            is AnalyzeEvent.UpdateModels -> updateModels(event.value)
            is AnalyzeEvent.UpdateBatchId -> updateBatchId(event.value)
            is AnalyzeEvent.ClearResponse -> clearAiResponses()
            is AnalyzeEvent.ClearAlert -> updateAlert(null)
            is AnalyzeEvent.Chat -> chat(event.word)
            is AnalyzeEvent.FetchBatch -> fetchBatch(event.batchId)
            is AnalyzeEvent.CreateBatch -> createBatch()
            is AnalyzeEvent.UpdatePrompt.FromString -> updatePrompt(event.value)
            is AnalyzeEvent.UpdatePrompt.FromVerse -> updatePrompt(event.word, event.verse)
            is AnalyzeEvent.SaveReport -> saveReport()
            else -> Unit
        }
    }

    private fun findSingletonWords(apostropheIsSeparator: Boolean) {
        screenModelScope.launch {
            updateProgress(
                Progress(0f, getString(Res.string.finding_singleton_words))
            )

            val totalVerses = verses.size
            val tempMap = mutableMapOf<String, SingletonWord>()
            val wordsRegex = if (apostropheIsSeparator) {
                nonApostropheRegex
            } else apostropheRegex

            withContext(Dispatchers.Default) {
                verses.forEachIndexed { index, verse ->
                    val words = wordsRegex.findAll(verse.text).map { it.value }

                    words.forEach { word ->
                        if (word.trim().isEmpty()) return@forEach

                        val w = tempMap.getOrElse(word) { SingletonWord(0, verse) }
                        tempMap[word] = w.copy(count = w.count + 1)
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
                tempMap
                    .sortedByKeyWith(compareBy { it.lowercase() })
                    .filterValues { it.count == 1 }
//                    .entries.take(20)
//                    .associate { it.key to it.value }
            )

            println("words count: ${_state.value.singletons.size}")

            updateProgress(null)
        }
    }

    private fun chat(word: String) {
        screenModelScope.launch {
            if (_state.value.models.isEmpty()) {
                updateAlert(getString(Res.string.no_model_selected))
                return@launch
            }

            updateProgress(Progress(0f, getString(Res.string.asking_ai)))
            clearAiResponses()
            withContext(Dispatchers.Default) {
                try {
                    val chatResult = watAiApi.chat(ChatRequest(
                        models = _state.value.models,
                        prompt = _state.value.prompt!!
                    ))
                    chatResult.onSuccess {
                        updateAiResponses(it.associateBy({ it.model }, { it.result }))
                        updateConsensus(
                            makeConsensus(listOf(AiResponse(word, it)))
                                .firstNotNullOf { it.value }
                        )

                        updateSingletons(
                            _state.value.singletons.mapValues { (key, value) ->
                                if (key == word) {
                                    value.copy(result = _state.value.consensus)
                                } else {
                                    value
                                }
                            }
                        )
                    }.onError {
                        updateAlert(it.description)
                    }
                } catch (e: Exception) {
                    updateAlert(e.message)
                }
            }
            updateProgress(null)
        }
    }

    private fun fetchBatch(batchId: String) {
        screenModelScope.launch {
            updateBatchProgress(0f)

            println(batchId)

            var status = BatchStatus.QUEUED
            val completeStatuses = listOf(
                BatchStatus.COMPLETE,
                BatchStatus.ERRORED,
                BatchStatus.TERMINATED,
                BatchStatus.UNKNOWN
            )
            while (status !in completeStatuses) {
                watAiApi.getBatch(batchId).onSuccess { batch ->
                    batch.details.output?.let { parseBatch(batch) }
                    status = batch.details.status

                    val current = batch.details.progress.completed + batch.details.progress.failed
                    val total = batch.details.progress.total.toFloat()
                    updateBatchProgress(current / total)
                }.onError {
                    println("error occurred: ${it.description}")
                }
                delay(BATCH_REQUEST_DELAY)
            }

            updateBatchProgress(-1f)
        }
    }

    private fun createBatch() {
        screenModelScope.launch {
            if (_state.value.models.isEmpty()) {
                updateAlert(getString(Res.string.no_model_selected))
                return@launch
            }

            println(_state.value.models.size)
            println(_state.value.singletons.size)

            val requests = _state.value.singletons.map { (key, word) ->
                BatchRequest(
                    id = key,
                    prompt = getPrompt(key, word.ref),
                    models = _state.value.models
                )
            }

            val json = buildJsonlFile(requests)
            val source = json.asSource()

            watAiApi.createBatch(source).onSuccess {
                println(it.id)
                _event.send(AnalyzeEvent.BatchCreated(it.id))
            }.onError {
                updateAlert(it.description)
            }
        }
    }

    private fun parseBatch(batch: Batch) {
        batch.details.output?.let { words ->
            val consensusMap = makeConsensus(words)
            consensusMap.forEach { (word, answer) ->
                updateSingletons(
                    _state.value.singletons.mapValues { (key, value) ->
                        if (key == word) {
                            value.copy(result = answer)
                        } else {
                            value
                        }
                    }
                )
            }
        }
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

            val words = _state.value.singletons
                .map { (key, value) ->
                    val builder = StringBuilder()
                    builder.append(key)
                    builder.append(",")
                    builder.append(value.ref.bookName)
                    builder.append(" (${value.ref.bookSlug})")
                    builder.append(",")
                    builder.append(value.ref.chapter)
                    builder.append(",")
                    builder.append(value.ref.number)
                    builder.append(",")

                    value.result?.models?.forEach {
                        builder.append(it.consensus.name)
                        builder.append(",")
                    }

                    builder.append(value.result?.consensus?.name)
                    builder.toString()
                }
                .joinToString("\n")

            val report = header.toString() + words

            FileKit.saveFile(
                baseName = "report",
                extension = "csv",
                bytes = report.encodeToByteArray()
            )

            updateAlert(getString(Res.string.report_saved))
        }
    }

    private fun updateSingletons(words: Map<String, SingletonWord>) {
        _state.update {
            it.copy(singletons = words)
        }
        checkBatchCreateReady()
    }

    private fun updateModels(models: List<String>) {
        _state.update {
            it.copy(models = models)
        }
        checkBatchCreateReady()
    }

    private fun updateBatchId(batchId: String?) {
        _state.update {
            it.copy(batchId = batchId)
        }
        checkBatchCreateReady()
    }

    private fun updatePrompt(prompt: String) {
        _state.update {
            it.copy(prompt = prompt)
        }
    }

    private fun updatePrompt(word: String, verse: Verse) {
        _state.update {
            it.copy(prompt = getPrompt(word, verse))
        }
    }

    private fun getPrompt(word: String, verse: Verse): String {
        return """
            In the ${language.angName} translation of the Bible verse
            ${verse.bookName} (${verse.bookSlug}) ${verse.chapter}:${verse.number},
            the word '$word' appears. Determine if '$word' in this context is a proper noun,
            a misspelling/typo, or something else. Provide only one of the following answers:
            proper noun, misspelling/typo, something else. Do not provide any explanation.
        """.trimIndent().replace("\n", " ")
    }

    private fun updateProgress(progress: Progress?) {
        _state.update {
            it.copy(progress = progress)
        }
    }

    private fun updateAlert(message: String?) {
        _state.update {
            it.copy(alert = message)
        }
    }

    private fun clearAiResponses() {
        _state.update {
            it.copy(
                aiResponses = emptyMap(),
                consensus = null
            )
        }
    }

    private fun updateBatchProgress(progress: Float) {
        _state.update {
            it.copy(batchProgress = progress)
        }
    }

    private fun updateAiResponses(responses: Map<String, String>) {
        _state.update {
            it.copy(aiResponses = responses)
        }
    }

    private fun checkBatchCreateReady() {
        if (_state.value.batchId != null) return
        if (_state.value.models.isEmpty()) return
        if (_state.value.singletons.isEmpty()) return

        screenModelScope.launch {
            _event.send(AnalyzeEvent.ReadyToCreateBatch)
        }
    }

    private fun updateConsensus(consensus: ConsensusResult?) {
        _state.update {
            it.copy(consensus = consensus)
        }
    }

    private fun makeConsensus(result: List<AiResponse>): Map<String, ConsensusResult> {
        val consensusMap = mutableMapOf<String, ConsensusResult>()

        result.forEach { response ->
            var misspellCount = 0
            var properNameCount = 0
            var somethingElseCount = 0
            response.results.forEach { result ->
                // Limit long answers to 20 characters
                val limit = min(20, result.result.length)
                val answer = result.result.substring(0, limit).lowercase()

                when {
                    answer.contains("proper name") -> properNameCount++
                    answer.contains("proper noun") -> properNameCount++
                    answer.contains("misspell") -> misspellCount++
                    answer.contains("typo") -> misspellCount++
                    answer.contains("something else") -> somethingElseCount++
                }
            }
            consensusMap[response.id] = ConsensusResult(
                models = response.results.map {
                    ModelConsensus(
                        model = it.model,
                        consensus = Consensus.of(it.result)
                    )
                },
                consensus = findWinner(
                    misspellCount,
                    properNameCount,
                    somethingElseCount
                )
            )
        }

        return consensusMap
    }

    private fun findWinner(misspell: Int, properName: Int, somethingElse: Int): Consensus {
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

        val ratio = max / _state.value.models.size.toFloat()
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