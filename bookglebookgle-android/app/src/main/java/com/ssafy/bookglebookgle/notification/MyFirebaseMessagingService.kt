package com.ssafy.bookglebookgle.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.MainActivity
import com.ssafy.bookglebookgle.repository.fcm.FcmRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmRepository: FcmRepository

    override fun onNewToken(token: String) {
        Log.i("FCM", "새 토큰: $token")
        // 로그인 상태에선 서버 등록
        fcmRepository.registerTokenAsync(token = token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "onMessageReceived() 호출됨. " +
                "hasNotif=${message.notification != null}, data=${message.data}")
        val title = message.notification?.title ?: message.data["title"] ?: "알림"
        val body  = message.notification?.body  ?: message.data["body"]  ?: "새 소식이 도착했어요"

        // 딥링크용 부가 데이터 (예: groupId)
        val groupId = message.data["groupId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("groupId", groupId)
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.w("FCM", "알림 권한 없음 → 표시 생략")
            return
        }

        val notif = NotificationCompat.Builder(this, NotificationChannels.DEFAULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(this).notify(Random.nextInt(), notif)
    }
}
