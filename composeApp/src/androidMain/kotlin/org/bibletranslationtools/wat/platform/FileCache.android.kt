package org.bibletranslationtools.wat.platform

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.security.MessageDigest

actual class FileCache(private val context: Context) {
    private val cacheDir: File by lazy { context.cacheDir }

    private fun urlToFileName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(url.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    actual suspend fun get(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, urlToFileName(url))
        if (file.exists()) {
            file.readBytes()
        } else {
            null
        }
    }

    actual suspend fun put(url: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, urlToFileName(url))
        file.writeBytes(data)
    }
}

actual fun createFileCache(): FileCache {
    val context: Context = getKoin().get()
    return FileCache(context)
}
