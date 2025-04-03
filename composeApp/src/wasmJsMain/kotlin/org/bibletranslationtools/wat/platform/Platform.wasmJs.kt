package org.bibletranslationtools.wat.platform

import io.github.mxaln.kotlin.document.store.core.DataStore
import io.github.mxaln.kotlin.document.store.stores.browser.BrowserStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual val appDirPath: String get() = "/"

actual val dbStore: DataStore = BrowserStore

    actual val httpClientEngine: HttpClientEngine
    get() {
        return Js.create()
    }

actual fun applyLocale(iso: String) {
    println("Applying locale not implemented in wasmJs yet")
}