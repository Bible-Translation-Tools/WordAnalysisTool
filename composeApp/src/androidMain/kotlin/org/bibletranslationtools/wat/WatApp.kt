package org.bibletranslationtools.wat

import android.app.Application
import org.bibletranslationtools.wat.di.initKoin
import org.koin.android.ext.koin.androidContext

class WatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(applicationContext)
        }
    }
}