package org.bibletranslationtools.wat.platform

expect class FileCache {
    /**
     * Retrieves file data from the cache for a given URL.
     * @return The file content as a ByteArray, or null if not found.
     */
    suspend fun get(url: String): ByteArray?

    /**
     * Stores file data in the cache under a given URL.
     */
    suspend fun put(url: String, data: ByteArray)
}

expect fun createFileCache(): FileCache