package org.bibletranslationtools.wat.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.Source
import kotlinx.io.readByteArray
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.unknown_error
import wordanalysistool.composeapp.generated.resources.unknown_error_details

interface ApiError

sealed interface ApiResult<out D, out E: ApiError> {
    data class Success<out D>(val data: D) : ApiResult<D, Nothing>
    data class Error<out E: ApiError>(val error: E) : ApiResult<Nothing, E>
}

inline fun <T, E: ApiError, R> ApiResult<T, E>.map(map: (T) -> R): ApiResult<R, E> {
    return when(this) {
        is ApiResult.Error -> ApiResult.Error(error)
        is ApiResult.Success -> ApiResult.Success(map(data))
    }
}

fun <T, E: ApiError> ApiResult<T, E>.asEmptyDataResult(): EmptyResult<E> {
    return map {}
}

inline fun <T, E: ApiError> ApiResult<T, E>.onSuccess(action: (T) -> Unit): ApiResult<T, E> {
    return when(this) {
        is ApiResult.Error -> this
        is ApiResult.Success -> {
            action(data)
            this
        }
    }
}

inline fun <T, E: ApiError> ApiResult<T, E>.onError(action: (E) -> Unit): ApiResult<T, E> {
    return when(this) {
        is ApiResult.Error -> {
            action(error)
            this
        }
        is ApiResult.Success -> this
    }
}

typealias EmptyResult<E> = ApiResult<Unit, E>

suspend fun get(
    httpClient: HttpClient,
    url: String,
    headers: Map<String, String> = emptyMap()
): NetworkResponse {
    return runNetworkRequest {
        httpClient.get(url) {
            headers {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
        }
    }
}

suspend fun postFile(
    httpClient: HttpClient,
    url: String,
    file: Source,
    headers: Map<String, String> = emptyMap()
): NetworkResponse {
    return runNetworkRequest {
        httpClient.post(urlString = url) {
            setBody(ByteReadChannel(file.readByteArray()))
            contentType(ContentType.Application.OctetStream)
            headers {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
        }
    }
}

suspend fun post(
    httpClient: HttpClient,
    url: String,
    body: Any
): NetworkResponse {
    return runNetworkRequest {
        httpClient.post(url) {
            setBody(body)
            contentType(ContentType.Application.Json)
        }
    }
}

private suspend fun runNetworkRequest(request: suspend () -> HttpResponse): NetworkResponse {
    return try {
        getNetworkResponse(request())
    } catch (e: Exception) {
        return NetworkResponse(
            null,
            NetworkError(
                ErrorType.ClientError,
                -1,
                getString(Res.string.unknown_error_details, e.message ?: "")
            )
        )
    }
}

private suspend fun getNetworkResponse(response: HttpResponse): NetworkResponse {
    return when (response.status.value) {
        in 200..299 -> NetworkResponse(response, null)
        401 -> NetworkResponse(
            null,
            NetworkError(ErrorType.Unauthorized, response.status.value, response.body<String>())
        )
        in 400..499 -> NetworkResponse(
            null,
            NetworkError(ErrorType.RequestError, response.status.value, response.body<String>())
        )
        in 500..599 ->
            NetworkResponse(
                null,
                NetworkError(
                    ErrorType.ServerError,
                    response.status.value,
                    response.status.description
                )
            )
        else -> NetworkResponse(
            null,
            NetworkError(
                ErrorType.Unknown,
                response.status.value,
                getString(Res.string.unknown_error)
            )
        )
    }
}