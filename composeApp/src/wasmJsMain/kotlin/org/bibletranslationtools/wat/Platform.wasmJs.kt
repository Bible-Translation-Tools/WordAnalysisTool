package org.bibletranslationtools.wat

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual val httpClientEngine: HttpClientEngine
    get() {
        return Js.create()
    }