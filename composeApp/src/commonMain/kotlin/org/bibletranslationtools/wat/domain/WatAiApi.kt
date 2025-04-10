package org.bibletranslationtools.wat.domain

import com.appstractive.jwt.JWT
import com.appstractive.jwt.from
import config.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.io.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.bibletranslationtools.wat.http.ApiResult
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.NetworkError
import org.bibletranslationtools.wat.http.delete
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
    val errored: Boolean,
    @SerialName("last_error")
    val lastError: String?,
    val results: List<ModelResponse>?
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
    @SerialName("ietf_code")
    val ietfCode: String,
    @SerialName("resource_type")
    val resourceType: String,
    val details: BatchDetails
)

@Serializable
private data class TokenUser(
    val username: String,
    val email: String,
)

@Serializable
data class User(
    val username: String,
    val email: String,
    val token: Token
) {
    companion object {
        fun fromToken(token: Token): User {
            val jsonObject: JsonObject = JWT.from(token.accessToken).claims
            val tokenUser = JsonLenient.decodeFromJsonElement<TokenUser>(jsonObject)

            return User(
                username = tokenUser.username,
                email = tokenUser.email,
                token = token
            )
        }
    }
}

@Serializable
data class Token(
    val accessToken: String
)

interface WatAiApi {
    suspend fun getAuthUrl(): ApiResult<String, NetworkError>
    suspend fun getAuthToken(): ApiResult<Token, NetworkError>
    suspend fun verifyUser(accessToken: String): ApiResult<Boolean, NetworkError>
    suspend fun getBatch(
        ietfCode: String,
        resourceType: String,
        accessToken: String
    ): ApiResult<Batch, NetworkError>

    suspend fun createBatch(
        ietfCode: String,
        resourceType: String,
        file: Source,
        accessToken: String
    ): ApiResult<Batch, NetworkError>

    suspend fun deleteBatch(
        ietfCode: String,
        resourceType: String,
        accessToken: String
    ): ApiResult<Boolean, NetworkError>
}

class WatAiApiImpl(
    private val httpClient: HttpClient
) : WatAiApi {

    private companion object {
        const val BASE_URL = BuildConfig.WAT_BASE_URL
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
                ApiResult.Success(response.data.body<Token>())
            }

            response.error != null -> {
                ApiResult.Error(response.error)
            }

            else -> ApiResult.Error(
                NetworkError(ErrorType.Unknown, -1, getString(Res.string.unknown_error))
            )
        }
    }

    override suspend fun verifyUser(accessToken: String): ApiResult<Boolean, NetworkError> {
        val response = get(
            httpClient = httpClient,
            url = "$BASE_URL/api/verify",
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Accept" to "application/json"
            )
        )
        return when {
            response.data != null -> {
                ApiResult.Success(
                    response.data.body<Boolean>()
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

    override suspend fun getBatch(
        ietfCode: String,
        resourceType: String,
        accessToken: String
    ): ApiResult<Batch, NetworkError> {
        val response = get(
            httpClient = httpClient,
            url = "$BASE_URL/api/batch/$ietfCode/$resourceType",
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Accept" to "application/json"
            )
        )
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

    override suspend fun createBatch(
        ietfCode: String,
        resourceType: String,
        file: Source,
        accessToken: String
    ): ApiResult<Batch, NetworkError> {
        val response = postFile(
            httpClient = httpClient,
            url = "$BASE_URL/api/batch/$ietfCode/$resourceType",
            file,
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Accept" to "application/json"
            )
        )
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

    override suspend fun deleteBatch(
        ietfCode: String,
        resourceType: String,
        accessToken: String
    ): ApiResult<Boolean, NetworkError> {
        val response = delete(
            httpClient = httpClient,
            url = "$BASE_URL/api/batch/$ietfCode/$resourceType",
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Accept" to "application/json"
            )
        )
        return when {
            response.data != null -> {
                ApiResult.Success(
                    response.data.body<Boolean>()
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
        val scope = "openid email profile read:user write:repository".encodeURLPathPart()
        val builder = StringBuilder()
        builder.append(AUTH_URL)
        builder.append("?client_id=${BuildConfig.WACS_CLIENT_ID}")
        builder.append("&redirect_uri=${"$BASE_URL/auth/callback".encodeURLPathPart()}")
        builder.append("&response_type=code")
        builder.append("&scope=$scope")
        builder.append("&state=$state")
        return builder.toString()
    }
}