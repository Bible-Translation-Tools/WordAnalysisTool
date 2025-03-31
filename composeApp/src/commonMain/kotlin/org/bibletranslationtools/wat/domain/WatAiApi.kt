package org.bibletranslationtools.wat.domain

import config.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.io.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bibletranslationtools.wat.http.ApiResult
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.NetworkError
import org.bibletranslationtools.wat.http.get
import org.bibletranslationtools.wat.http.postFile
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.unknown_error
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class BatchStatus {
    @SerialName("queued") QUEUED,
    @SerialName("running") RUNNING,
    @SerialName("paused") PAUSED,
    @SerialName("errored") ERRORED,
    @SerialName("terminated") TERMINATED,
    @SerialName("complete") COMPLETE,
    @SerialName("waiting") WAITING,
    @SerialName("waitingForPause") WAITING_FOR_PAUSE,
    @SerialName("unknown") UNKNOWN
}

@Serializable
data class ModelResponse(
    val model: String,
    val result: String
)

@Serializable
data class AiResponse(
    val id: String,
    val results: List<ModelResponse>
)

@Serializable
data class BatchRequest(
    val id: String,
    val prompt: String,
    val models: List<String>
)

@Serializable
data class BatchProgress(
    val completed: Int,
    val failed: Int,
    val total: Int
)

@Serializable
data class BatchDetails(
    val status: BatchStatus,
    val progress: BatchProgress,
    val error: String?,
    val output: List<AiResponse>? = null
)

@Serializable
data class Batch(
    val id: String,
    val details: BatchDetails
)

@Serializable
data class User(
    val username: String,
    val email: String
)

@Serializable
data class Token(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String
)

interface WatAiApi {
    suspend fun getAuthUrl(): ApiResult<String, NetworkError>
    suspend fun getAuthToken(): ApiResult<Token, NetworkError>
    suspend fun getAuthUser(accessToken: String): ApiResult<User, NetworkError>
    suspend fun getBatch(id: String): ApiResult<Batch, NetworkError>
    suspend fun createBatch(file: Source): ApiResult<Batch, NetworkError>
}

class WatAiApiImpl(
    private val httpClient: HttpClient
) : WatAiApi {

    private companion object {
        const val BASE_URL = BuildConfig.BASE_API
        const val WACS_API = "https://content.bibletranslationtools.org/api/v1"
        const val AUTH_URL = "https://content.bibletranslationtools.org/login/oauth/authorize"
    }

    private var state: String? = null

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getAuthUrl(): ApiResult<String, NetworkError> {
        state = Uuid.random().toString()
        val authUrl = buildAuthUrl(state!!)
        return ApiResult.Success(authUrl)
    }

    override suspend fun getAuthToken(): ApiResult<Token, NetworkError> {
        val response = get(httpClient, "$BASE_URL/auth/tokens/$state")
        return when {
            response.data != null -> {
                ApiResult.Success(
                    response.data.body<Token>()
                )
            }
            response.error != null -> {
                ApiResult.Error(response.error)
            }
            else -> ApiResult.Error(
                NetworkError(ErrorType.Unknown, -1, getString(Res.string.unknown_error))
            )
        }
    }

    override suspend fun getAuthUser(accessToken: String): ApiResult<User, NetworkError> {
        val response = get(
            httpClient = httpClient,
            url = "$WACS_API/user",
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Accept" to "application/json"
            )
        )

        return when {
            response.data != null -> {
                ApiResult.Success(
                    response.data.body<User>()
                )
            }
            response.error != null -> {
                ApiResult.Error(response.error)
            }
            else -> ApiResult.Error(
                NetworkError(ErrorType.Unknown, -1, getString(Res.string.unknown_error))
            )
        }
    }

    override suspend fun getBatch(id: String): ApiResult<Batch, NetworkError> {
        val response = get(httpClient, "$BASE_URL/batch/$id")
        return when {
            response.data != null -> {
                ApiResult.Success(
                    response.data.body<Batch>()
                )
            }
            response.error != null -> {
                ApiResult.Error(response.error)
            }
            else -> ApiResult.Error(
                NetworkError(ErrorType.Unknown, -1, getString(Res.string.unknown_error))
            )
        }
    }

    override suspend fun createBatch(file: Source): ApiResult<Batch, NetworkError> {
        val response = postFile(httpClient, "$BASE_URL/batch", file)
        return when {
            response.data != null -> {
                ApiResult.Success(
                    response.data.body<Batch>()
                )
            }
            response.error != null -> {
                ApiResult.Error(response.error)
            }
            else -> ApiResult.Error(
                NetworkError(ErrorType.Unknown, -1, getString(Res.string.unknown_error))
            )
        }
    }

    private fun buildAuthUrl(state: String): String {
        val builder = StringBuilder()
        builder.append(AUTH_URL)
        builder.append("?client_id=${BuildConfig.WACS_CLIENT}")
        builder.append("&redirect_uri=${BuildConfig.WACS_CALLBACK}")
        builder.append("&response_type=code")
        builder.append("&state=$state")
        return builder.toString()
    }
}