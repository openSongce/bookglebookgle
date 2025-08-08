package com.ssafy.bookglebookgle

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import com.ssafy.bookglebookgle.ui.theme.BookgleBookgleTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ssafy.bookglebookgle.navigation.MainNavigation
import androidx.activity.ComponentActivity
import com.ssafy.bookglebookgle.notification.NotificationChannels
import com.ssafy.bookglebookgle.notification.NotificationPermissionRequester
import com.ssafy.bookglebookgle.repository.fcm.FcmRepository
import javax.inject.Inject

private const val TAG = "싸피_MainActivity"
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var fcmRepository: FcmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationChannels.createDefaultChannel(this)

        setTheme(R.style.Theme_BookgleBookgle)

        // 시스템 스플래시를 최대한 빨리 제거
        val splashScreen = installSplashScreen()

        // 즉시 스플래시 제거
        splashScreen.setKeepOnScreenCondition { false }

        // 토큰 확인
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { if (BuildConfig.DEBUG) android.util.Log.i("FCM", "현재 토큰: $it") }

        enableEdgeToEdge()
        setContent {
            NotificationPermissionRequester(
                autoRequest = true,
                onGranted = { fcmRepository.registerTokenAsync()}
            )

            BookgleBookgleTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }





}
