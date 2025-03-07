package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.data.Word
import org.bibletranslationtools.wat.data.sortedByKeyWith
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.finding_singleton_words

class AnalyzeViewModel(
    private val verses: List<Verse>
) : ScreenModel {

    private val _allVerses = MutableStateFlow(verses)
    val allVerses = _allVerses.asStateFlow()

    var progress by mutableStateOf<Progress?>(null)
        private set

    private val _singletonWords = MutableStateFlow<Map<String, Word>>(emptyMap())
    val singletonWords = _singletonWords.asStateFlow()

    fun findSingletonWords() {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.finding_singleton_words))

            val totalVerses = verses.size
            val tempMap = mutableMapOf<String, Word>()

            verses.forEachIndexed { index, verse ->
                val words = verse.text.split("\\P{L}+".toRegex())
                words.forEach { word ->
                    if (word.trim().isEmpty()) return@forEach

                    val w = tempMap.getOrPut(word) {
                        Word(0, listOf())
                    }
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
}