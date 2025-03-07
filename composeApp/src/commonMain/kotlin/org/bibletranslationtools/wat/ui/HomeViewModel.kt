package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.downloading_usfm
import wordanalysistool.composeapp.generated.resources.fetching_heart_languages
import wordanalysistool.composeapp.generated.resources.fetching_resource_types
import wordanalysistool.composeapp.generated.resources.unknown_error

class HomeViewModel(
    private val bielGraphQlApi: BielGraphQlApi,
    private val downloadUsfm: DownloadUsfm,
    private val usfmBookSource: UsfmBookSource
) : ScreenModel {

    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<Progress?>(null)
        private set

    private val _heartLanguages = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val heartLanguages = _heartLanguages.asStateFlow()

    fun fetchHeartLanguages() {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.fetching_heart_languages))
            _heartLanguages.value = bielGraphQlApi.getHeartLanguages()
            progress = null
        }
    }

    suspend fun fetchResourceTypesForHeartLanguage(
        ietfCode: String
    ): List<String> {
        progress = Progress(0f, getString(Res.string.fetching_resource_types))
        val resourceTypes = bielGraphQlApi.getUsfmForHeartLanguage(ietfCode).keys.toList()
        progress = null
        return resourceTypes
    }

    suspend fun fetchUsfmForHeartLanguage(
        ietfCode: String,
        resourceType: String
    ): List<Verse> {
        val books = bielGraphQlApi.getBooksForTranslation(ietfCode, resourceType)
        val totalBooks = books.size
        val allVerses = mutableListOf<Verse>()

        progress = Progress(0f, getString(Res.string.downloading_usfm))

        withContext(Dispatchers.Default) {
            books.forEachIndexed { index, book ->
                book.url?.let { url ->
                    val currentProgress = (index+1)/totalBooks.toFloat()
                    val response = downloadUsfm(url)

                    response.onSuccess { bytes ->
                        println(url)
                        allVerses.addAll(usfmBookSource.parse(bytes.decodeToString()))
                    }.onError { err ->
                        error = err.description ?: getString(Res.string.unknown_error)
                        allVerses.clear()
                        return@withContext
                    }

                    progress = Progress(
                        currentProgress,
                        getString(Res.string.downloading_usfm)
                    )
                }
            }
        }

        progress = null
        return allVerses
    }

    fun clearError() {
        error = null
    }
}