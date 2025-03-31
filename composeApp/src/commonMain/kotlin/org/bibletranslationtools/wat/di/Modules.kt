package org.bibletranslationtools.wat.di

import com.russhwolf.settings.ExperimentalSettingsApi
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.domain.UsfmBookSourceImpl
import org.bibletranslationtools.wat.domain.WatAiApi
import org.bibletranslationtools.wat.domain.WatAiApiImpl
import org.bibletranslationtools.wat.domain.createAiHttpClient
import org.bibletranslationtools.wat.domain.createSimpleHttpClient
import org.bibletranslationtools.wat.platform.httpClientEngine
import org.bibletranslationtools.wat.ui.AnalyzeViewModel
import org.bibletranslationtools.wat.ui.HomeViewModel
import org.bibletranslationtools.wat.ui.LoginViewModel
import org.bibletranslationtools.wat.ui.SettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalSettingsApi::class)
val sharedModule = module {
    singleOf(::BielGraphQlApi)
    single { DownloadUsfm(createSimpleHttpClient(httpClientEngine)) }
    factory { WatAiApiImpl(createAiHttpClient(httpClientEngine)) }.bind<WatAiApi>()

    factoryOf(::UsfmBookSourceImpl).bind<UsfmBookSource>()

    // view models
    factoryOf(::SettingsViewModel)
    factoryOf(::LoginViewModel)
    factoryOf(::HomeViewModel)
    factory { (language: LanguageInfo, verses: List<Verse>) ->
        AnalyzeViewModel(language, verses, get())
    }
}
