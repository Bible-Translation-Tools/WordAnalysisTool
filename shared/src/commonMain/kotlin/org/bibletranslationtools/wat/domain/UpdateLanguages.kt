package org.bibletranslationtools.wat.domain

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import org.bibletranslationtools.wat.data.Language
import org.bibletranslationtools.wat.http.ApiResult
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.NetworkError
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess

class UpdateLanguages(
    private val watApi: WatApi,
    private val downloadUsfm: DownloadUsfm
) {

    /**
     * Downloads the latest langnames.json from [LANGNAMES_URL] and imports it into the
     * languages database. Returns the number of imported languages.
     */
    suspend fun fromUrl(accessToken: String): ApiResult<Int, NetworkError> {
        var result: ApiResult<Int, NetworkError>? = null
        downloadUsfm(LANGNAMES_URL)
            .onSuccess { bytes ->
                result = importBytes(bytes, accessToken)
            }
            .onError { error ->
                result = ApiResult.Error(error)
            }
        return result ?: ApiResult.Error(
            NetworkError(ErrorType.Unknown, -1, "unknown error")
        )
    }

    /**
     * Imports a langnames.json file picked from the local file system into the
     * languages database. Returns the number of imported languages.
     */
    suspend fun fromFile(
        file: PlatformFile,
        accessToken: String
    ): ApiResult<Int, NetworkError> {
        return importBytes(file.readBytes(), accessToken)
    }

    private suspend fun importBytes(
        bytes: ByteArray,
        accessToken: String
    ): ApiResult<Int, NetworkError> {
        val languages = try {
            JsonLenient.decodeFromString<List<Language>>(bytes.decodeToString())
        } catch (e: Exception) {
            return ApiResult.Error(
                NetworkError(
                    ErrorType.ClientError,
                    -1,
                    e.message ?: "invalid languages file"
                )
            )
        }
        return watApi.importLanguages(languages, accessToken)
    }

    private companion object {
        const val LANGNAMES_URL =
            "https://langnames.bibleineverylanguage.org/langnames.json"
    }
}
