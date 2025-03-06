package org.bibletranslationtools.wat.http

data class NetworkError(
    val type: ErrorType,
    val description: String?
) : ApiError

enum class ErrorType {
    NoInternet,
    Serialization,
    RequestTimeout,
    PayloadTooLarge,
    ServerError,
    Unknown;
}