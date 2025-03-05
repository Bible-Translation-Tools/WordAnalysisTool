package org.bibletranslationtools.wat

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

actual val httpClientEngine: HttpClientEngine
    get() {
        return CIO.create()
    }