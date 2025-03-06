package org.bibletranslationtools.wat.di

import io.ktor.client.HttpClient
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.httpClientEngine
import org.bibletranslationtools.wat.ui.HomeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private val httpClient = HttpClient(httpClientEngine)

val sharedModule = module {
    singleOf(::BielGraphQlApi)
    singleOf(::httpClient)
    singleOf(::DownloadUsfm)

    // view models
    factoryOf(::HomeViewModel)
}
