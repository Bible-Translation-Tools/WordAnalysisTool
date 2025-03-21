package org.bibletranslationtools.wat.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bibletranslationtools.wat.data.Consensus
import org.bibletranslationtools.wat.data.LanguageInfo
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
import org.bibletranslationtools.wat.http.ApiResult
import org.bibletranslationtools.wat.http.NetworkError
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.asking_ai
import wordanalysistool.composeapp.generated.resources.finding_singleton_words
import wordanalysistool.composeapp.generated.resources.no_model_selected
import kotlin.math.min

private const val BATCH_REQUEST_DELAY = 5000L

data class AnalyzeState(
    val batchId: String? = "0924d1d9-468e-490a-a064-d293ce3890d5",
    val batchProgress: Float = -1f,
    val prompt: String? = null,
    val consensus: Consensus? = null,
    val aiResponses: Map<String, String?> = emptyMap(),
    val models: List<String> = emptyList(),
    val error: String? = null,
    val progress: Progress? = null,
    val apostropheIsSeparator: Boolean = false
)

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

    private val _singletonWords = MutableStateFlow<Map<String, SingletonWord>>(emptyMap())
    val singletonWords = _singletonWords
        .onStart { findSingletonWords() }
        .stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    private val apostropheRegex = "[\\p{L}'’]+(?<!['’])".toRegex()
    private val nonApostropheRegex = "\\p{L}+".toRegex()

    fun findSingletonWords() {
        screenModelScope.launch {
            updateProgress(
                Progress(0f, getString(Res.string.finding_singleton_words))
            )

            delay(1000)

            val totalVerses = verses.size
            val tempMap = mutableMapOf<String, SingletonWord>()
            val wordsRegex = if (_state.value.apostropheIsSeparator) {
                nonApostropheRegex
            } else apostropheRegex

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

            _singletonWords.value = tempMap
                .sortedByKeyWith(compareBy { it.lowercase() })
                .filterValues { it.count == 1 }
//                .entries.take(20)
//                .associate { it.key to it.value }

            println("words count: ${_singletonWords.value.size}")

            updateProgress(null)
        }
    }

    fun setupModels(models: List<String>) {
        _state.update {
            it.copy(models = models)
        }
    }

    fun batch() {
        screenModelScope.launch {
            if (_state.value.models.isEmpty()) {
                updateError(getString(Res.string.no_model_selected))
                return@launch
            }

            updateBatchProgress(0f)

            _state.value.batchId?.let {
                var status = BatchStatus.QUEUED
                val completeStatuses = listOf(
                    BatchStatus.COMPLETE,
                    BatchStatus.ERRORED,
                    BatchStatus.TERMINATED,
                    BatchStatus.UNKNOWN
                )
                while (status !in completeStatuses) {
                    watAiApi.getBatch(it).onSuccess { batch ->
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
            } ?: run {
                val batch = createBatch()
                batch.onSuccess {
                    println(it.id)
                    updateBatchId(it.id)

                    var status = BatchStatus.QUEUED
                    val completeStatuses = listOf(
                        BatchStatus.COMPLETE,
                        BatchStatus.ERRORED,
                        BatchStatus.TERMINATED,
                        BatchStatus.UNKNOWN
                    )
                    while (status !in completeStatuses) {
                        watAiApi.getBatch(it.id).onSuccess { batch ->
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
                }.onError {
                    updateError(it.description)
                }
            }

            updateBatchProgress(-1f)
        }
    }

    fun chat(word: String) {
        screenModelScope.launch {
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
                        _singletonWords.update { map ->
                            map.mapValues { (key, value) ->
                                if (key == word) {
                                    value.copy(consensus = _state.value.consensus)
                                } else {
                                    value
                                }
                            }
                        }
                    }.onError {
                        updateError(it.description)
                    }
                } catch (e: Exception) {
                    updateError(e.message)
                }
            }
            updateProgress(null)
        }
    }

    private suspend fun createBatch(): ApiResult<Batch, NetworkError> {
        val requests = _singletonWords.value.map { (key, word) ->
            BatchRequest(
                id = key,
                prompt = getPrompt(key, word.ref),
                models = _state.value.models
            )
        }

        val json = buildJsonlFile(requests)
        val source = json.asSource()
        return watAiApi.createBatch(source)
    }

    private fun parseBatch(batch: Batch) {
        batch.details.output?.let { words ->
            val consensusMap = makeConsensus(words)
            consensusMap.forEach { (word, answer) ->
                _singletonWords.update { map ->
                    map.mapValues { (key, value) ->
                        if (key == word) {
                            value.copy(consensus = answer)
                        } else {
                            value
                        }
                    }
                }
            }
        }
    }

    fun updatePrompt(prompt: String) {
        _state.update {
            it.copy(prompt = prompt)
        }
    }

    fun updatePrompt(word: String, verse: Verse) {
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

    fun updateApostropheIsSeparator(isSeparator: Boolean) {
        _state.update {
            it.copy(apostropheIsSeparator = isSeparator)
        }
    }

    private fun updateProgress(progress: Progress?) {
        _state.update {
            it.copy(progress = progress)
        }
    }

    fun updateError(error: String?) {
        _state.update {
            it.copy(error = error)
        }
    }

    fun clearAiResponses() {
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

    private fun updateBatchId(id: String?) {
        _state.update {
            it.copy(batchId = id)
        }
    }

    private fun updateAiResponses(responses: Map<String, String>) {
        _state.update {
            it.copy(aiResponses = responses)
        }
    }

    private fun updateConsensus(consensus: Consensus?) {
        _state.update {
            it.copy(consensus = consensus)
        }
    }

    private fun makeConsensus(result: List<AiResponse>): Map<String, Consensus> {
        val consensusMap = mutableMapOf<String, Consensus>()

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
            consensusMap[response.id] =
                findWinner(misspellCount, properNameCount, somethingElseCount)
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