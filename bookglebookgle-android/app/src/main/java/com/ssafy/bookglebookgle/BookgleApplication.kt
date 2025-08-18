package com.ssafy.bookglebookgle

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.kakao.sdk.common.KakaoSdk
import com.ssafy.bookglebookgle.notification.NotificationChannels
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.asn1.x500.style.RFC4519Style.description
import javax.inject.Inject

@HiltAndroidApp
class BookgleApplication : Application(), Configuration.Provider  {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)

        PDFBoxResourceLoader.init(this.applicationContext)

        val nativeKey = BuildConfig.KAKAO_NATIVE_KEY
        KakaoSdk.init(this, nativeKey)
        NotificationChannels.createDefaultChannel(this)

        createNotificationChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "group_creation_channel"
            val name = "모임 생성 알림"
            val descriptionText = "모임 생성 완료 및 실패 알림"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
