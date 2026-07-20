package org.bibletranslationtools.wat.platform

import android.content.Context
import android.os.LocaleList
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import org.koin.mp.KoinPlatform.getKoin
import java.util.Locale

actual val appDirPath: String
    get() {
        val context: Context = getKoin().get()
        return context.getExternalFilesDir(null)?.canonicalPath
            ?: throw IllegalArgumentException("External files dir not found")
    }

actual val httpClientEngine: HttpClientEngine
    get() {
        return Android.create()
    }

actual fun applyLocale(iso: String) {
    val context: Context = getKoin().get()
    val locale = Locale.forLanguageTag(iso)
    Locale.setDefault(locale)
    val config = context.resources.configuration
    config.setLocales(LocaleList(locale))
}