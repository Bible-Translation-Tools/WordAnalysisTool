package org.bibletranslationtools.wat.platform

import io.github.mxaln.kotlin.document.store.core.DataStore
import io.ktor.client.engine.HttpClientEngine

expect val dbStore: DataStore
expect val httpClientEngine: HttpClientEngine
expect val appDirPath: String
expect fun applyLocale(iso: String)