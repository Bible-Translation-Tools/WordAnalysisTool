package org.bibletranslationtools.wat.di

import org.bibletranslationtools.wat.data.VerseRef
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.UpdateLanguages
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.domain.UsfmBookSourceImpl
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.domain.WatApiImpl
import org.bibletranslationtools.wat.domain.createAiHttpClient
import org.bibletranslationtools.wat.domain.createSimpleHttpClient
import org.bibletranslationtools.wat.platform.createFileCache
import org.bibletranslationtools.wat.platform.httpClientEngine
import org.bibletranslationtools.wat.ui.AnalyzeViewModel
import org.bibletranslationtools.wat.ui.HomeViewModel
import org.bibletranslationtools.wat.ui.LoginViewModel
import org.bibletranslationtools.wat.ui.ReviewViewModel
import org.bibletranslationtools.wat.ui.SettingsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val sharedModule = module {
    singleOf(::BielGraphQlApi)
    singleOf(::UpdateLanguages)
    single { DownloadUsfm(createSimpleHttpClient(httpClientEngine)) }
    factory { WatApiImpl(createAiHttpClient(httpClientEngine)) }.bind<WatApi>()

    single { createFileCache() }
    factoryOf(::UsfmBookSourceImpl).bind<UsfmBookSource>()

    // view models
    factoryOf(::LoginViewModel)
    factory { (user: User) ->
        HomeViewModel(
            user = user,
            bielGraphQlApi = get(),
            watApi = get()
        )
    }
    factory { (user: User) ->
        SettingsViewModel(
            user = user,
            updateLanguages = get()
        )
    }
    factory { (ietfCode: String, resourceType: String, user: User, batchId: String?) ->
        ReviewViewModel(
            ietfCode = ietfCode,
            resourceType = resourceType,
            user = user,
            batchId = batchId,
            watApi = get(),
            bielGraphQlApi = get(),
            downloadUsfm = get(),
            usfmBookSource = get()
        )
    }
    factory { (ietfCode: String, resourceType: String, verses: VerseRef, user: User) ->
        AnalyzeViewModel(
            ietfCode = ietfCode,
            resourceType = resourceType,
            verses = verses,
            user = user,
            watApi = get(),
            bielGraphQlApi = get()
        )
    }
}
