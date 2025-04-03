package org.bibletranslationtools.wat.platform

import io.github.mxaln.kotlin.document.store.core.DataStore
import io.github.mxaln.kotlin.document.store.stores.leveldb.LevelDBStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.util.Locale

actual val appDirPath: String
    get() {
        val propertyKey = "user.home"
        val appDirPath = "${System.getProperty(propertyKey)}/WordAnalysisTool"
        val appDir = File(appDirPath)
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir.canonicalPath
    }

actual val dbStore: DataStore = LevelDBStore.open(appDirPath)

actual val httpClientEngine: HttpClientEngine
    get() {
        return CIO.create()
    }

actual fun applyLocale(iso: String) {
    val locale = Locale.of(iso)
    Locale.setDefault(locale)
}