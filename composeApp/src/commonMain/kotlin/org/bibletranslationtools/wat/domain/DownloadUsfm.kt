package org.bibletranslationtools.wat.domain

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsBytes
import org.bibletranslationtools.wat.http.ApiResult
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.NetworkError
import org.bibletranslationtools.wat.http.getResponse
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.unknown_error

class DownloadUsfm(private val httpClient: HttpClient) {
    suspend operator fun invoke(url: String): ApiResult<ByteArray, NetworkError> {
        val response = getResponse(httpClient, url)

        return when {
            response.data != null -> {
                ApiResult.Success(response.data.bodyAsBytes())
            }
            response.error != null -> {
                ApiResult.Error(response.error)
            }
            else -> ApiResult.Error(
                NetworkError(ErrorType.Unknown, getString(Res.string.unknown_error))
            )
        }
    }
}