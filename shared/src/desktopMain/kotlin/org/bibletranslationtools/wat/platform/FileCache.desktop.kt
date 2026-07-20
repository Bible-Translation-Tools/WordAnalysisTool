package org.bibletranslationtools.wat.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

actual class FileCache {
    private val cacheDir: Path = Paths.get(appDirPath, ".cache")

    init {
        Files.createDirectories(cacheDir)
    }

    private fun urlToFileName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(url.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    actual suspend fun get(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val fileName = urlToFileName(url)
        val filePath = cacheDir.resolve(fileName)

        if (Files.exists(filePath)) {
            Files.readAllBytes(filePath)
        } else {
            null
        }
    }

    actual suspend fun put(url: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            val fileName = urlToFileName(url)
            val filePath = cacheDir.resolve(fileName)
            Files.write(filePath, data)
        }
    }
}

actual fun createFileCache(): FileCache = FileCache()