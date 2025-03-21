package org.bibletranslationtools.wat.domain

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.io.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bibletranslationtools.wat.http.ApiResult
import org.bibletranslationtools.wat.http.ErrorType
import org.bibletranslationtools.wat.http.NetworkError
import org.bibletranslationtools.wat.http.get
import org.bibletranslationtools.wat.http.post
import org.bibletranslationtools.wat.http.postFile
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.unknown_error

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
data class ChatRequest(
    val models: List<String>,
    val prompt: String
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

interface WatAiApi {
    suspend fun chat(request: ChatRequest): ApiResult<List<ModelResponse>, NetworkError>
    suspend fun getBatch(id: String): ApiResult<Batch, NetworkError>
    suspend fun createBatch(file: Source): ApiResult<Batch, NetworkError>
}

class WatAiApiImpl(
    private val httpClient: HttpClient
) : WatAiApi {

    private companion object {
        const val BASE_URL = "https://wat-worker.mxaln.workers.dev"
    }

    override suspend fun chat(request: ChatRequest): ApiResult<List<ModelResponse>, NetworkError> {
        val response = post(httpClient, "$BASE_URL/chat", request)

        return when {
            response.data != null -> {
                ApiResult.Success(
                    response.data.body<List<ModelResponse>>()
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
}