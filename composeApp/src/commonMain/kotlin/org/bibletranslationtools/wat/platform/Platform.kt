package org.bibletranslationtools.wat.platform

import io.ktor.client.engine.HttpClientEngine

expect val httpClientEngine: HttpClientEngine
expect fun applyLocale(iso: String)