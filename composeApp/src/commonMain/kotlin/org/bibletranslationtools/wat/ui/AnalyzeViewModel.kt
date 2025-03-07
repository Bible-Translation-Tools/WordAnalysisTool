package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.data.Word
import org.bibletranslationtools.wat.data.sortedByKeyWith
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.asking_ai
import wordanalysistool.composeapp.generated.resources.finding_singleton_words

class AnalyzeViewModel(
    private val language: LanguageInfo,
    private val resourceType: String,
    private val verses: List<Verse>
) : ScreenModel {

    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<Progress?>(null)
        private set

    var aiResponse by mutableStateOf<String?>(null)
        private set

    private val _singletonWords = MutableStateFlow<Map<String, Word>>(emptyMap())
    val singletonWords = _singletonWords
        .onStart { findSingletonWords() }
        .stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    private val geminiModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = ""
    )

    fun findSingletonWords() {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.finding_singleton_words))

            val totalVerses = verses.size
            val tempMap = mutableMapOf<String, Word>()

            verses.forEachIndexed { index, verse ->
                val words = verse.text.split("\\P{L}+".toRegex())
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
                    (index+1)/totalVerses.toFloat(),
                    getString(Res.string.finding_singleton_words)
                )
            }

            _singletonWords.value = tempMap
                .sortedByKeyWith(compareBy { it.lowercase() })
                .filter { it.value.count == 1 }

            progress = null
        }
    }

    fun askAi(word: String, verse: Verse) {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.asking_ai))
            aiResponse = withContext(Dispatchers.Default) {
                try {
                    geminiModel.generateContent(
                        """
                            Check the word: "$word" in this verse: ${verse.text}.
                            This is a verse from the Bible. The language is ${language.name}.
                            The verse is from the book ${verse.bookName} (${verse.bookSlug}),
                            chapter ${verse.chapter}, verse ${verse.number}.
                            Define only one thing: if this is a proper name, misspell/typo or 
                            something else. Answer like this: 
                            This word is proper name | misspell/typo | something else and short
                            explanation.
                        """.trimIndent()
                    ).text
                } catch (e: Exception) {
                    error = e.message
                    ""
                }
            }
            progress = null
        }
    }

    fun clearError() {
        error = null
    }
}