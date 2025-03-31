package org.bibletranslationtools.wat.domain

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.serialization.json.Json

internal fun createSimpleHttpClient(
    engine: HttpClientEngine
): HttpClient {
    val configuration:  HttpClientConfig<*>.() -> Unit = {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 5)
            exponentialDelay()
        }
    }
    return HttpClient(engine, configuration)
}

internal fun createAiHttpClient(
    engine: HttpClientEngine
): HttpClient {
    val configuration:  HttpClientConfig<*>.() -> Unit = {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 5)
            exponentialDelay()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
        install(ContentNegotiation) {
            register(ContentType.Application.Json, KotlinxSerializationConverter(JsonLenient))
        }
    }
    return HttpClient(engine, configuration)
}

val JsonLenient = Json {
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
}