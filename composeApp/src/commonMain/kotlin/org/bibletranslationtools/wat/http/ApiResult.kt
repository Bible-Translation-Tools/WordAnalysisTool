package org.bibletranslationtools.wat.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.serialization.SerializationException
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.unknown_error

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

suspend fun getResponse(
    httpClient: HttpClient,
    url: String,
    params: Map<String, String> = mapOf()
): NetworkResponse {
    val response = try {
        httpClient.get(url) {
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    } catch(e: UnresolvedAddressException) {
        return NetworkResponse(null, NetworkError(ErrorType.NoInternet, e.message))
    } catch(e: SerializationException) {
        return NetworkResponse(null, NetworkError(ErrorType.Serialization, e.message))
    } catch (e: HttpRequestTimeoutException) {
        return NetworkResponse(null, NetworkError(ErrorType.RequestTimeout, e.message))
    } catch (e: Exception) {
        return NetworkResponse(
            null,
            NetworkError(ErrorType.Unknown, getString(Res.string.unknown_error))
        )
    }

    return when (response.status.value) {
        in 200..299 -> NetworkResponse(response, null)
        413 -> NetworkResponse(
            null,
            NetworkError(ErrorType.PayloadTooLarge, response.status.description)
        )
        in 500..599 -> NetworkResponse(
            null,
            NetworkError(ErrorType.ServerError, response.status.description)
        )
        else -> NetworkResponse(
            null,
            NetworkError(ErrorType.Unknown, getString(Res.string.unknown_error))
        )
    }
}