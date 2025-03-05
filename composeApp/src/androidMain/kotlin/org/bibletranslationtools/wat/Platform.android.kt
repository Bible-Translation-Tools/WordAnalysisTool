package org.bibletranslationtools.wat

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android

actual val httpClientEngine: HttpClientEngine
    get() {
        return Android.create()
    }