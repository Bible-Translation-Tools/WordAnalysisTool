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
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
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
import org.bibletranslationtools.wat.domain.GeminiModel
import org.bibletranslationtools.wat.domain.OpenAiModel
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.asking_ai
import wordanalysistool.composeapp.generated.resources.finding_singleton_words
import wordanalysistool.composeapp.generated.resources.invalid_ai_selected
import wordanalysistool.composeapp.generated.resources.no_ai_model_selected

class AnalyzeViewModel(
    private val language: LanguageInfo,
    private val verses: List<Verse>
) : ScreenModel {

    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<Progress?>(null)
        private set

    var aiResponse by mutableStateOf<String?>(null)
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

    private var geminiModel: GenerativeModel? = null
    private var openAiModel: OpenAI? = null
    private var openAiModelId: ModelId? = null

    fun findSingletonWords() {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.finding_singleton_words))

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
            progress = Progress(0f, getString(Res.string.asking_ai))
            when {
                geminiModel != null -> askGemini(prompt)
                openAiModel != null -> askOpenAi(prompt)
                else -> error = getString(Res.string.no_ai_model_selected)
            }
            progress = null
        }
    }

    private suspend fun askGemini(prompt: String) {
        aiResponse = withContext(Dispatchers.Default) {
            try {
                geminiModel?.generateContent(prompt)?.text
            } catch (e: Exception) {
                error = e.message
                null
            }
        }
    }

    @OptIn(BetaOpenAI::class)
    private suspend fun askOpenAi(prompt: String) {
        aiResponse = withContext(Dispatchers.Default) {
            try {
                openAiModel?.chatCompletion(
                    request = ChatCompletionRequest(
                        model = openAiModelId!!,
                        messages = listOf(
                            ChatMessage(
                                role = Role.User,
                                content = prompt
                            )
                        ),
                        store = false
                    )
                )?.choices
                    ?.firstOrNull()
                    ?.message
                    ?.content
            } catch (e: Exception) {
                error = e.message
                null
            }
        }
    }

    fun setupModel(aiApi: String, aiModel: String, aiApiKey: String) {
        screenModelScope.launch {
            // reset
            geminiModel = null
            openAiModel = null

            when (aiApi) {
                AiApi.GEMINI.name -> setupGemini(aiModel, aiApiKey)
                AiApi.OPENAI.name -> setupOpenAi(aiModel, aiApiKey)
                else -> error = getString(Res.string.invalid_ai_selected)
            }
        }
    }

    private fun setupGemini(aiModel: String, aiApiKey: String) {
        geminiModel = GenerativeModel(
            modelName = GeminiModel.getOrDefault(aiModel).value,
            apiKey = aiApiKey
        )
    }

    private fun setupOpenAi(aiModel: String, aiApiKey: String) {
        openAiModel = OpenAI(token = aiApiKey)
        openAiModelId = ModelId(OpenAiModel.getOrDefault(aiModel).value)
    }

    fun updatePrompt(prompt: String) {
        this.prompt = prompt
    }

    fun updatePrompt(word: String, verse: Verse) {
        prompt = """
            Check the word "$word" in the bible verse 
            ${verse.bookName} (${verse.bookSlug}) ${verse.chapter}:${verse.number} ${verse.text}.
            The language is ${language.name} (${language.angName}).
            Define only whether this a proper name, misspell/typo or 
            something else. If it's a misspell/typo, provide the correct answer.
        """.trimIndent()
    }

    fun updateApostropheIsSeparator(isSeparator: Boolean) {
        apostropheIsSeparator = isSeparator
    }

    fun clearError() {
        error = null
    }

    fun clearAiResponse() {
        aiResponse = null
    }
}