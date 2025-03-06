package org.bibletranslationtools.wat.di

import io.ktor.client.HttpClient
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.domain.UsfmBookSourceImpl
import org.bibletranslationtools.wat.platform.httpClientEngine
import org.bibletranslationtools.wat.ui.HomeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private val httpClient = HttpClient(httpClientEngine)

val sharedModule = module {
    singleOf(::BielGraphQlApi)
    singleOf(::httpClient)
    singleOf(::DownloadUsfm)
    factoryOf(::UsfmBookSourceImpl).bind<UsfmBookSource>()

    // view models
    factoryOf(::HomeViewModel)
}
