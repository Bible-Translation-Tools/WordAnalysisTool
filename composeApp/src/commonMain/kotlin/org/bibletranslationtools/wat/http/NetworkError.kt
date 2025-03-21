package org.bibletranslationtools.wat.http

data class NetworkError(
    val type: ErrorType,
    val code: Int,
    val description: String?
) : ApiError

enum class ErrorType {
    ClientError,
    RequestError,
    ServerError,
    Unknown;
}