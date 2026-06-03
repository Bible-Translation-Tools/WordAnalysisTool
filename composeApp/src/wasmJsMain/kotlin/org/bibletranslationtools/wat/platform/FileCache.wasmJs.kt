package org.bibletranslationtools.wat.platform

import io.ktor.util.toJsArray
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.fetch.Response
import org.w3c.workers.Cache
import kotlin.ByteArray

@OptIn(ExperimentalWasmJsInterop::class)
actual class FileCache(private val cacheName: String = "wat-cache") {

    private suspend fun openCache(): Cache =
        window.caches.open(cacheName).await()

    actual suspend fun get(url: String): ByteArray? {
        val cache = openCache()
        val matched = cache.match(url).await()

        return matched?.let {
            val response = it.unsafeCast<Response>()
            val arrayBuffer = response.arrayBuffer().await()

            val jsInt8Array = Int8Array(arrayBuffer)
            ByteArray(jsInt8Array.length) { index ->
                getByteFromArrayBuffer(arrayBuffer, index)
            }
        }
    }

    actual suspend fun put(url: String, data: ByteArray) {
        val cache = openCache()
        val jsUint8Array = data.toJsArray()
        val response = Response(jsUint8Array)
        cache.put(url, response).await()
    }
}

actual fun createFileCache(): FileCache {
    return FileCache()
}

@JsFun("(buffer, index) => new Int8Array(buffer)[index]")
private external fun getByteFromArrayBuffer(buffer: ArrayBuffer, index: Int): Byte