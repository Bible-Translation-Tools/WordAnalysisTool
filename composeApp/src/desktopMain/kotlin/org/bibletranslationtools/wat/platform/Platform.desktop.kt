package org.bibletranslationtools.wat.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import java.util.Locale

actual val httpClientEngine: HttpClientEngine
    get() {
        return CIO.create()
    }

actual fun applyLocale(iso: String) {
    val locale = Locale.of(iso)
    Locale.setDefault(locale)
}