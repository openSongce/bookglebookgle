package com.ssafy.bookglebookgle

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookgleApplication : Application()  {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this.applicationContext)
    }
}
