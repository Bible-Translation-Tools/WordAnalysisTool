package org.bibletranslationtools.wat.ui

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.ktor.client.HttpClient
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.ContentInfo

class HomeViewModel(
    private val bielGraphQlApi: BielGraphQlApi,
    private val httpClient: HttpClient
) : ScreenModel {

    private val _heartLanguages = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val heartLanguages = _heartLanguages.asStateFlow()

    private val _gatewayLanguages = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val gatewayLanguages = _gatewayLanguages.asStateFlow()

    private val _usfmForHeartLanguage = MutableStateFlow<List<ContentInfo>>(
        emptyList()
    )
    val usfmForHeartLanguage = _usfmForHeartLanguage.asStateFlow()

    fun fetchHeartLanguages() {
        screenModelScope.launch {
            _heartLanguages.emit(bielGraphQlApi.getHeartLanguages())
        }
    }

    fun fetchGatewayLanguages() {
        screenModelScope.launch {
            _gatewayLanguages.emit(bielGraphQlApi.getGatewayLanguages())
        }
    }

    suspend fun fetchResourceTypesForHeartLanguage(
        ietfCode: String
    ): List<String> {
        return bielGraphQlApi.getUsfmForHeartLanguage(ietfCode).keys.toList()
    }

    fun fetchUsfmForHeartLanguage(ietfCode: String, resourceType: String) {
        screenModelScope.launch {
            _usfmForHeartLanguage.emit(
                bielGraphQlApi.getBooksForTranslation(ietfCode, resourceType)
            )
        }
    }
}