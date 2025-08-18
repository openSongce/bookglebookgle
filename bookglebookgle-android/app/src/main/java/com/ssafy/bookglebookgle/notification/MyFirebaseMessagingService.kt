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
import okhttp3.internal.notify
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmRepository: FcmRepository

    companion object {
        const val TYPE_CHAT = "CHAT"
        const val TYPE_MEETING_START = "MEETING_START"
    }

    override fun onNewToken(token: String) {
        Log.i("FCM", "새 토큰: $token")
        // 로그인 상태에선 서버 등록
        fcmRepository.registerTokenAsync(token = token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "onMessageReceived() 호출됨. " +
                "hasNotif=${message.notification != null}, data=${message.data}")
        // 메시지 데이터 추출
        val type = message.data["type"] ?: ""
        val groupId = message.data["groupId"]
        val groupName = message.data["title"] ?: "알림"
        val messageBody = message.data["body"] ?: "모임이 시작되었습니다. 지금 바로 참여하세요!"

        // 타입별 알림 제목과 내용 설정
        val (title, body) = when (type) {
            TYPE_CHAT -> {
                val notificationTitle = groupName
                val notificationBody = if (messageBody.isNotEmpty()) messageBody else "새로운 메시지가 도착했습니다"
                Pair(notificationTitle, notificationBody)
            }
            TYPE_MEETING_START -> {
                val notificationTitle = "$groupName 모임 시작"
                val notificationBody = "모임이 시작되었습니다! 참여해보세요"
                Pair(notificationTitle, notificationBody)
            }
            else -> {
                // 기본 처리
                val defaultTitle = message.notification?.title ?: groupName
                val defaultBody = message.notification?.body ?: messageBody.ifEmpty { "새 소식이 도착했어요" }
                Pair(defaultTitle, defaultBody)
            }
        }

        // 수정된 Intent 생성 - 포그라운드 대응
        val intent = createDeepLinkIntent(type, groupId)
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("FCM", "알림 권한 없음 → 표시 생략")
            return
        }

        // 알림 생성 및 표시
        val notification = NotificationCompat.Builder(this, NotificationChannels.DEFAULT)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // 긴 텍스트 지원
            .build()

        NotificationManagerCompat.from(this).notify(Random.nextInt(), notification)
    }

    private fun createDeepLinkIntent(type: String, groupId: String?): Intent {
        return Intent(this, MainActivity::class.java).apply {
            // 액션과 데이터를 추가하여 고유성 확보
            action = "FCM_NOTIFICATION_$type"
            putExtra("notification_timestamp", System.currentTimeMillis())

            // 포그라운드에서는 스플래시를 거치지 않도록 플래그 수정
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP

            when (type) {
                TYPE_CHAT -> {
                    putExtra("deeplink_type", "chat")
                    putExtra("groupId", groupId)
                }
                TYPE_MEETING_START -> {
                    putExtra("deeplink_type", "group_detail")
                    putExtra("groupId", groupId)
                }
                else -> {
                    putExtra("deeplink_type", "group_detail") // 기본값 설정
                    putExtra("groupId", groupId)
                }
            }
        }
    }
}
