package org.bibletranslationtools.wat.platform

import io.ktor.client.engine.HttpClientEngine

expect val httpClientEngine: HttpClientEngine
expect val appDirPath: String
expect fun applyLocale(iso: String)
expect suspend fun saveFile(bytes: ByteArray, filename: String, extension: String)