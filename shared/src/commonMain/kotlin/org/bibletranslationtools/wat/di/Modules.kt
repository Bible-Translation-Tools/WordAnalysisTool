package org.bibletranslationtools.wat.di

import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.User
import org.bibletranslationtools.wat.domain.WatApi
import org.bibletranslationtools.wat.domain.WatApiImpl
import org.bibletranslationtools.wat.domain.createAiHttpClient
import org.bibletranslationtools.wat.platform.httpClientEngine
import org.bibletranslationtools.wat.ui.AnalyzeViewModel
import org.bibletranslationtools.wat.ui.HomeViewModel
import org.bibletranslationtools.wat.ui.LoginViewModel
import org.bibletranslationtools.wat.ui.ReviewViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val sharedModule = module {
    singleOf(::BielGraphQlApi)
    factory { WatApiImpl(createAiHttpClient(httpClientEngine)) }.bind<WatApi>()

    // view models
    factoryOf(::LoginViewModel)
    factory { (user: User) ->
        HomeViewModel(
            user = user,
            bielGraphQlApi = get(),
            watApi = get()
        )
    }
    factory { (ietfCode: String, resourceType: String, user: User, batchId: String?) ->
        ReviewViewModel(
            ietfCode = ietfCode,
            resourceType = resourceType,
            user = user,
            batchId = batchId,
            watApi = get(),
            bielGraphQlApi = get()
        )
    }
    factory { (ietfCode: String, resourceType: String, user: User) ->
        AnalyzeViewModel(
            ietfCode = ietfCode,
            resourceType = resourceType,
            user = user,
            watApi = get(),
            bielGraphQlApi = get()
        )
    }
}
