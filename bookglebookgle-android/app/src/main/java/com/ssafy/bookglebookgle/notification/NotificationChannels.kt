package com.ssafy.bookglebookgle.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val DEFAULT = "default"

    fun createDefaultChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                DEFAULT,
                "일반 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "북글북글 기본 알림 채널" }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }
}
