package org.bibletranslationtools.wat.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

actual val appDirPath: String get() = "/"

actual val httpClientEngine: HttpClientEngine
    get() {
        return Js.create()
    }

actual fun applyLocale(iso: String) {
    println("Applying locale not implemented in web yet")
}

actual suspend fun saveFile(bytes: ByteArray, filename: String, extension: String) {
    FileKit.download(bytes, "$filename.$extension")
}