package org.bibletranslationtools.wat.di

import io.github.mxaln.kotlin.document.store.core.KotlinDocumentStore
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.domain.UsfmBookSourceImpl
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.domain.WatApiImpl
import org.bibletranslationtools.wat.domain.WordDataSource
import org.bibletranslationtools.wat.domain.WordDataSourceImpl
import org.bibletranslationtools.wat.domain.createAiHttpClient
import org.bibletranslationtools.wat.domain.createSimpleHttpClient
import org.bibletranslationtools.wat.platform.dbStore
import org.bibletranslationtools.wat.platform.httpClientEngine
import org.bibletranslationtools.wat.ui.AnalyzeViewModel
import org.bibletranslationtools.wat.ui.HomeViewModel
import org.bibletranslationtools.wat.ui.LoginViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val sharedModule = module {
    single { KotlinDocumentStore(dbStore) }
    singleOf(::WordDataSourceImpl).bind<WordDataSource>()

    singleOf(::BielGraphQlApi)
    single { DownloadUsfm(createSimpleHttpClient(httpClientEngine)) }
    factory { WatApiImpl(createAiHttpClient(httpClientEngine)) }.bind<WatApi>()

    factoryOf(::UsfmBookSourceImpl).bind<UsfmBookSource>()

    // view models
    factoryOf(::LoginViewModel)
    factory { (user: User) -> HomeViewModel(get(), get(), get(), get(), user) }
    factory { (language: LanguageInfo, resourceType: String, verses: List<Verse>, user: User) ->
        AnalyzeViewModel(language, resourceType, verses, user, get())
    }
}
