package org.bibletranslationtools.wat.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual val httpClientEngine: HttpClientEngine
    get() {
        return Js.create()
    }