package com.ssafy.bookglebookgle

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookgleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this.applicationContext)
    }
}