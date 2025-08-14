package com.ssafy.bookglebookgle.util

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.ssafy.bookglebookgle.MainActivity
import com.ssafy.bookglebookgle.R
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x500.style.RFC4519Style.description
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "group_creation_channel"
        private const val HIGH_PRIORITY_CHANNEL_ID = "group_creation_high_priority"
        private const val SUCCESS_NOTIFICATION_ID = 1001
        private const val FAILURE_NOTIFICATION_ID = 1002
        private const val PROCESSING_NOTIFICATION_ID = 1003
        private const val TIMEOUT_NOTIFICATION_ID = 1004
        private const val CANCELLED_NOTIFICATION_ID = 1005

        interface InAppNotificationCallback {
            fun onGroupCreationSuccess(groupName: String, isOcrProcessed: Boolean)
            fun onGroupCreationFailed(errorMessage: String)
            fun onGroupCreationTimeout()
            fun onGroupCreationCancelled()
        }

        var inAppCallback: InAppNotificationCallback? = null
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * 알림 채널 생성 (Android 8.0 이상)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 일반 우선순위 채널
            val channel = NotificationChannel(
                CHANNEL_ID,
                "모임 생성 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "모임 생성 관련 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // 높은 우선순위 채널 (헤드업 알림용)
            val highPriorityChannel = NotificationChannel(
                HIGH_PRIORITY_CHANNEL_ID,
                "중요 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "모임 생성 완료 등 중요한 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                // 화면에 팝업으로 표시
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(highPriorityChannel)
        }
    }


    private fun isAppInForeground(): Boolean {
        return try {
            // ProcessLifecycleOwner 사용
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (e: Exception) {
            // ActivityManager 사용
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningTasks = activityManager.getRunningTasks(1)
                val currentTask = runningTasks.firstOrNull()
                currentTask?.topActivity?.packageName == context.packageName
            } catch (e2: Exception) {
                Log.w("NotificationHelper", "포그라운드 상태 확인 실패", e2)
                false
            }
        }
    }

    private fun createAppLaunchIntent(notificationType: String = ""): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", notificationType)
            putExtra("from_notification", true)
        }

        return PendingIntent.getActivity(
            context,
            notificationType.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showNotificationSmart(
        title: String,
        content: String,
        notificationType: String,
        notificationId: Int,
        bigText: String? = null,
        onInAppAction: (() -> Unit)? = null
    ) {
        val isInForeground = isAppInForeground()

        Log.d("NotificationHelper", "알림 표시: title=$title, 포그라운드=$isInForeground")

        if (isInForeground) {
            // 포그라운드일 때는 인앱 콜백 실행
            Log.d("NotificationHelper", "포그라운드 상태 - 인앱 처리")
            onInAppAction?.invoke()
        } else {
            // 백그라운드일 때는 푸시 알림 표시
            Log.d("NotificationHelper", "백그라운드 상태 - 푸시 알림 표시")
            showPushNotification(title, content, notificationType, notificationId, bigText)
        }
    }

    /**
     * 헤드업 알림(화면에 팝업)을 위한 고우선순위 알림 생성
     */
    private fun createHeadsUpNotification(
        title: String,
        content: String,
        notificationType: String,
        bigText: String? = null
    ): NotificationCompat.Builder {
        // 포그라운드일 때는 클릭 동작 없음, 백그라운드일 때만 앱 실행
        val pendingIntent = if (isAppInForeground()) {
            null // 포그라운드일 때는 클릭해도 아무 동작 안함
        } else {
            createAppLaunchIntent(notificationType) // 백그라운드일 때만 앱 실행
        }

        val builder = NotificationCompat.Builder(context, HIGH_PRIORITY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 높은 우선순위
            .setCategory(NotificationCompat.CATEGORY_STATUS) // 상태 카테고리
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 기본 사운드, 진동, LED
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 잠금화면에서도 표시
            .setTimeoutAfter(5000) // 5초 후 자동으로 사라짐

        // PendingIntent가 null이 아닐 때만 설정
        pendingIntent?.let {
            builder.setContentIntent(it)
        }

        // 긴 텍스트가 있으면 BigTextStyle 적용
        bigText?.let {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        return builder
    }

    private fun showPushNotification(
        title: String,
        content: String,
        notificationType: String,
        notificationId: Int,
        bigText: String? = null
    ) {
        if (!hasNotificationPermission()) return

        val pendingIntent = createAppLaunchIntent(notificationType)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // 긴 텍스트가 있으면 BigTextStyle 적용
        bigText?.let {
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        // 성공 알림에는 사운드와 진동 추가
        if (notificationType == "success") {
            notificationBuilder
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(0, 500, 200, 500))
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }


    /**
     * 처리 중 알림 (진행 상황 표시)
     */
    fun showProcessingNotification(groupName: String, message: String) {
        if (!hasNotificationPermission()) return

        val pendingIntent = createAppLaunchIntent("processing")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle("모임 생성 중")
            .setContentText("'$groupName' $message")
            .setProgress(0, 0, true) // 무한 진행바
            .setOngoing(true) // 스와이프로 삭제 불가
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(PROCESSING_NOTIFICATION_ID, notification)
        Log.d("NotificationHelper", "처리 중 알림 표시: $groupName - $message")
    }

    /**
     * 처리 중 알림 취소
     */
    fun cancelProcessingNotification() {
        notificationManager.cancel(PROCESSING_NOTIFICATION_ID)
    }

    /**
     * 타임아웃 전용 알림
     */
    fun showGroupCreationTimeoutNotification() {
        if (!hasNotificationPermission()) return

        val bigText = "모임 생성에 시간이 오래 걸리고 있습니다.\n잠시 후 다시 시도해주세요."

        val notification = createHeadsUpNotification(
            title = "모임 생성 시간 초과",
            content = "서버 응답 시간이 초과되었습니다",
            notificationType = "timeout",
            bigText = bigText
        ).build()

        notificationManager.notify(TIMEOUT_NOTIFICATION_ID, notification)
        Log.d("NotificationHelper", "타임아웃 알림 표시 (헤드업)")
    }

    /**
     * 작업 취소 전용 알림
     */
    fun showGroupCreationCancelledNotification() {
        if (!hasNotificationPermission()) return

        val bigText = "모임 생성 작업이 시스템에 의해 중단되었습니다.\n앱을 다시 열어서 모임 생성을 재시도해주세요."

        val notification = createHeadsUpNotification(
            title = "모임 생성 중단",
            content = "모임 생성 작업이 중단되었습니다",
            notificationType = "cancelled",
            bigText = bigText
        ).build()

        notificationManager.notify(CANCELLED_NOTIFICATION_ID, notification)
        Log.d("NotificationHelper", "취소 알림 표시 (헤드업)")
    }

    /**
     * 모임 생성 성공 알림 - 헤드업 알림으로 화면에 표시
     */
    fun showGroupCreationSuccessNotification(
        groupName: String,
        isOcrProcessed: Boolean,
        responseBody: String? = null
    ) {
        if (!hasNotificationPermission()) return

        val title = "모임 생성 완료!"
        val content = "'$groupName' 모임이 성공적으로 생성되었습니다!"

        val bigText = buildString {
            append("'$groupName' 모임이 성공적으로 생성되었습니다!\n")
            append("이제 모임에서 독서를 시작할 수 있습니다!")
        }

        // 성공 알림은 헤드업으로 표시하여 화면에 팝업으로 나타남
        val notification = createHeadsUpNotification(
            title = title,
            content = content,
            notificationType = "success",
            bigText = bigText
        )
            .setColor(ContextCompat.getColor(context, R.color.main_color)) // 알림 색상
            .build()

        notificationManager.notify(SUCCESS_NOTIFICATION_ID, notification)
        Log.d("NotificationHelper", "성공 알림 표시 (헤드업): $groupName (OCR: $isOcrProcessed)")
    }


    /**
     * 모임 생성 실패 알림 - 헤드업 알림으로 화면에 표시
     */
    fun showGroupCreationFailedNotification(errorMessage: String) {
        if (!hasNotificationPermission()) return

        val bigText = "모임 생성에 실패했습니다.\n잠시 후 다시 시도해주세요."

        val notification = createHeadsUpNotification(
            title = "모임 생성 실패",
            content = "모임 생성에 실패했습니다",
            notificationType = "failure",
            bigText = bigText
        )
            .setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            .build()

        notificationManager.notify(FAILURE_NOTIFICATION_ID, notification)
        Log.d("NotificationHelper", "실패 알림 표시 (헤드업): $errorMessage")
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}