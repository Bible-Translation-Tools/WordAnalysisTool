package org.bibletranslationtools.wat.di

import com.russhwolf.settings.ExperimentalSettingsApi
import io.ktor.client.HttpClient
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.domain.UsfmBookSourceImpl
import org.bibletranslationtools.wat.platform.httpClientEngine
import org.bibletranslationtools.wat.ui.AnalyzeViewModel
import org.bibletranslationtools.wat.ui.HomeViewModel
import org.bibletranslationtools.wat.ui.SettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private val httpClient = HttpClient(httpClientEngine)

@OptIn(ExperimentalSettingsApi::class)
val sharedModule = module {
    singleOf(::BielGraphQlApi)
    singleOf(::httpClient)
    singleOf(::DownloadUsfm)
    factoryOf(::UsfmBookSourceImpl).bind<UsfmBookSource>()

    // view models
    factoryOf(::HomeViewModel)
    factoryOf(::SettingsViewModel)
    factory { (language: LanguageInfo, verses: List<Verse>) ->
        AnalyzeViewModel(language, verses)
    }
}
