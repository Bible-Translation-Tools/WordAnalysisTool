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
import org.bibletranslationtools.wat.data.ContentInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.downloading_usfm
import wordanalysistool.composeapp.generated.resources.unknown_error

class HomeViewModel(
    private val bielGraphQlApi: BielGraphQlApi,
    private val downloadUsfm: DownloadUsfm,
    private val usfmBookSource: UsfmBookSource
) : ScreenModel {

    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<String?>(null)
        private set

    private val _heartLanguages = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val heartLanguages = _heartLanguages.asStateFlow()

    private val _gatewayLanguages = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val gatewayLanguages = _gatewayLanguages.asStateFlow()

    private val _usfmForHeartLanguage = MutableStateFlow<List<ContentInfo>>(emptyList())
    val usfmForHeartLanguage = _usfmForHeartLanguage.asStateFlow()

    private val _verses = MutableStateFlow<List<Verse>>(emptyList())
    val verses = _verses.asStateFlow()

    fun fetchHeartLanguages() {
        screenModelScope.launch {
            _heartLanguages.value = bielGraphQlApi.getHeartLanguages()
        }
    }

    fun fetchGatewayLanguages() {
        screenModelScope.launch {
            _gatewayLanguages.value = bielGraphQlApi.getGatewayLanguages()
        }
    }

    suspend fun fetchResourceTypesForHeartLanguage(
        ietfCode: String
    ): List<String> {
        return bielGraphQlApi.getUsfmForHeartLanguage(ietfCode).keys.toList()
    }

    fun fetchUsfmForHeartLanguage(ietfCode: String, resourceType: String) {
        screenModelScope.launch {
            _usfmForHeartLanguage.value =
                bielGraphQlApi.getBooksForTranslation(ietfCode, resourceType)
        }
    }

    fun fetchUsfm(url: String) {
        screenModelScope.launch {
            progress = getString(Res.string.downloading_usfm)
            withContext(Dispatchers.Default) {
                val response = downloadUsfm(url)
                response.onSuccess { bytes ->
                    println(bytes.decodeToString())
                    //usfmBookSource.import(bytes)
                    //loadBooks()
                    _verses.value = usfmBookSource.parse(bytes.decodeToString())
                }.onError { err ->
                    error = err.description ?: getString(Res.string.unknown_error)
                }
            }
            progress = null
        }
    }

    fun clearError() {
        error = null
    }

    fun clearVerses() {
        _verses.value = emptyList()
    }
}