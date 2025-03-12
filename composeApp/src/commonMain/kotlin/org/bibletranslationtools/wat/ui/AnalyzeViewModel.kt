package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.data.Word
import org.bibletranslationtools.wat.data.sortedByKeyWith
import org.bibletranslationtools.wat.domain.AiApi
import org.bibletranslationtools.wat.domain.ClaudeAiModel
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.OpenAiModel
import org.bibletranslationtools.wat.domain.QwenModel
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.asking_ai
import wordanalysistool.composeapp.generated.resources.claude_ai
import wordanalysistool.composeapp.generated.resources.claude_api_link
import wordanalysistool.composeapp.generated.resources.finding_singleton_words
import wordanalysistool.composeapp.generated.resources.gemini
import wordanalysistool.composeapp.generated.resources.no_ai_model_selected
import wordanalysistool.composeapp.generated.resources.openai
import wordanalysistool.composeapp.generated.resources.qwen
import wordanalysistool.composeapp.generated.resources.qwen_api_link

class AnalyzeViewModel(
    private val language: LanguageInfo,
    private val verses: List<Verse>
) : ScreenModel {

    private data class AiModel(val ai: OpenAI, val id: ModelId)

    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<Progress?>(null)
        private set

    var aiResponses by mutableStateOf<Map<AiApi, String?>>(emptyMap())
        private set

    var consensus by mutableStateOf<String?>(null)
        private set

    var prompt by mutableStateOf("")
        private set

    private var apostropheIsSeparator by mutableStateOf(false)

    private val apostropheRegex = "[\\p{L}'’]+(?<!['’])".toRegex()
    private val nonApostropheRegex = "\\p{L}+".toRegex()

    private val _singletonWords = MutableStateFlow<Map<String, Word>>(emptyMap())
    val singletonWords = _singletonWords
        .onStart { findSingletonWords() }
        .stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    private lateinit var geminiModel: GenerativeModel
    private lateinit var openAiModel: AiModel
    private lateinit var qwenModel: AiModel
    private lateinit var claudeAiModel: AiModel

    private val ais = mutableMapOf<AiApi, Boolean>()

    fun findSingletonWords() {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.finding_singleton_words))

            delay(1000)

            val totalVerses = verses.size
            val tempMap = mutableMapOf<String, Word>()
            val wordsRegex = if (apostropheIsSeparator) nonApostropheRegex else apostropheRegex

            verses.forEachIndexed { index, verse ->
                val words = wordsRegex.findAll(verse.text).map { it.value }

                words.forEach { word ->
                    if (word.trim().isEmpty()) return@forEach

                    val w = tempMap.getOrPut(word) { Word(0, listOf()) }
                    tempMap[word] = w.copy(
                        count = w.count + 1,
                        refs = if (!w.refs.contains(verse)) {
                            listOf(verse) + w.refs
                        } else w.refs
                    )
                }

                progress = Progress(
                    (index + 1) / totalVerses.toFloat(),
                    getString(Res.string.finding_singleton_words)
                )
            }

            _singletonWords.value = tempMap
                .sortedByKeyWith(compareBy { it.lowercase() })
                .filter { it.value.count == 1 }

            progress = null
        }
    }

    fun askAi() {
        screenModelScope.launch {
            clearAiResponses()

            if (ais.values.none { it }) {
                error = getString(Res.string.no_ai_model_selected)
                return@launch
            }

            try {
                ais.filter { it.value }.forEach { (ai, _) ->
                    val aiName = getAiName(ai)
                    progress = Progress(0f, getString(Res.string.asking_ai, aiName))

                    when (ai) {
                        AiApi.GEMINI -> askGemini(prompt)
                        AiApi.OPENAI -> askOpenAi(prompt, openAiModel, AiApi.OPENAI)
                        AiApi.QWEN -> askOpenAi(prompt, qwenModel, AiApi.QWEN)
                        AiApi.CLAUDE_AI -> askOpenAi(prompt, claudeAiModel, AiApi.CLAUDE_AI)
                    }
                }
            } catch (e: Exception) {
                error = e.message
            }

            makeConsensus()

            progress = null
        }
    }

    private suspend fun askGemini(prompt: String) {
        val aiName = getString(Res.string.gemini)
        progress = Progress(0f, getString(Res.string.asking_ai, aiName))

        val response = withContext(Dispatchers.Default) {
            try {
                geminiModel.generateContent(prompt).text
            } catch (e: Exception) {
                error = e.message
                null
            }
        }

        aiResponses = aiResponses + (AiApi.GEMINI to response?.lowercase()?.trim())
    }

    @OptIn(BetaOpenAI::class)
    private suspend fun askOpenAi(prompt: String, model: AiModel, api: AiApi) {
        val aiName = getAiName(api)
        progress = Progress(0f, getString(Res.string.asking_ai, aiName))

        val response = withContext(Dispatchers.Default) {
            try {
                model.ai.chatCompletion(
                    request = ChatCompletionRequest(
                        model = model.id,
                        messages = listOf(
                            ChatMessage(
                                role = Role.User,
                                content = prompt
                            )
                        ),
                        store = false
                    )
                ).choices
                    .firstOrNull()
                    ?.message
                    ?.content
            } catch (e: Exception) {
                error = e.message
                null
            }
        }

        aiResponses = aiResponses + (api to response?.lowercase()?.trim())
    }

    fun setupGemini(aiModel: String, aiApiKey: String, isActive: Boolean) {
        ais.put(AiApi.GEMINI, isActive)
        if (!isActive) return

        geminiModel = GenerativeModel(
            modelName = GeminiModel.getOrDefault(aiModel).value,
            apiKey = aiApiKey
        )
    }

    fun setupOpenAi(aiModel: String, aiApiKey: String, isActive: Boolean) {
        ais.put(AiApi.OPENAI, isActive)
        openAiModel = AiModel(
            ai = OpenAI(token = aiApiKey),
            id = ModelId(OpenAiModel.getOrDefault(aiModel).value)
        )
    }

    suspend fun setupQwen(aiModel: String, aiApiKey: String, isActive: Boolean) {
        ais.put(AiApi.QWEN, isActive)
        val host = OpenAIHost(baseUrl = getString(Res.string.qwen_api_link))
        qwenModel = AiModel(
            ai = OpenAI(token = aiApiKey, host = host),
            id = ModelId(QwenModel.getOrDefault(aiModel).value)
        )
    }

    suspend fun setupClaudeAi(aiModel: String, aiApiKey: String, isActive: Boolean) {
        ais.put(AiApi.CLAUDE_AI, isActive)
        val host = OpenAIHost(baseUrl = getString(Res.string.claude_api_link))
        claudeAiModel = AiModel(
            ai = OpenAI(token = aiApiKey, host = host),
            id = ModelId(ClaudeAiModel.getOrDefault(aiModel).value)
        )
    }

    fun updatePrompt(prompt: String) {
        this.prompt = prompt
    }

    fun updatePrompt(word: String, verse: Verse) {
        prompt = """
            Check the ${language.angName} word "$word" in the Bible verse 
            ${verse.bookName} (${verse.bookSlug}) ${verse.chapter}:${verse.number}.
            "${verse.text}"
            Define whether this is a proper name, misspell/typo or a something else. 
            The answer "should be only one" of these options: proper name, misspell/typo, something else.
            Do not explain your answer.
        """.trimIndent()
    }

    fun updateApostropheIsSeparator(isSeparator: Boolean) {
        apostropheIsSeparator = isSeparator
    }

    fun clearError() {
        error = null
    }

    fun clearAiResponses() {
        aiResponses = emptyMap()
        consensus = null
    }

    private suspend fun getAiName(ai: AiApi): String {
        return when (ai) {
            AiApi.GEMINI -> getString(Res.string.gemini)
            AiApi.OPENAI -> getString(Res.string.openai)
            AiApi.QWEN -> getString(Res.string.qwen)
            AiApi.CLAUDE_AI -> getString(Res.string.claude_ai)
        }
    }

    private fun makeConsensus() {
        var misspellCount = 0
        var properNameCount = 0
        var somethingElseCount = 0

        aiResponses.forEach { (_, response) ->
            response?.let {
                when {
                    it.contains("proper name") -> properNameCount++
                    it.contains("misspell") -> misspellCount++
                    it.contains("typo") -> misspellCount++
                    it.contains("something else") -> somethingElseCount++
                }
            }
        }

        consensus = findLargest(misspellCount, properNameCount, somethingElseCount)
    }

    fun findLargest(
        misspellCount: Int,
        properNameCount: Int,
        somethingElseCount: Int
    ): String {
        var largestValue = misspellCount
        var largestName = "misspell/typo"

        if (properNameCount > largestValue) {
            largestValue = properNameCount
            largestName = "proper name"
        }

        if (somethingElseCount > largestValue) {
            largestValue = somethingElseCount
            largestName = "something else"
        }

        return largestName
    }
}