package com.ssafy.bookglebookgle

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.notification.NotificationChannels
import com.ssafy.bookglebookgle.notification.NotificationPermissionRequester
import com.ssafy.bookglebookgle.repository.fcm.FcmRepository
import javax.inject.Inject

private const val TAG = "싸피_MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var fcmRepository: FcmRepository

    companion object {
        val deepLinkIntents = kotlinx.coroutines.flow.MutableSharedFlow<Intent>(
            extraBufferCapacity = 1
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationChannels.createDefaultChannel(this)

        setTheme(R.style.Theme_BookgleBookgle)

        // 시스템 스플래시 제거
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }

        // 최초 Intent 처리 → 스트림으로 방출
        intent?.let { emitDeepLinkIntent(it) }

        // 토큰 확인
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener {
            if (BuildConfig.DEBUG) android.util.Log.i(
                "FCM",
                "현재 토큰: $it"
            )
        }

        enableEdgeToEdge()

        // 알림 채널 생성
        NotificationChannels.createDefaultChannel(this)

        setContent {
            var navController: NavHostController? by remember { mutableStateOf(null) }
            NotificationPermissionRequester(
                autoRequest = true,
                onGranted = { fcmRepository.registerTokenAsync() }
            )

            BookgleBookgleTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    navController = nav

                    // 앱 시작 시 딥링크 처리
                    LaunchedEffect(Unit) {
                        intent?.let { handleInitialDeepLink(it, nav) }
                    }

                    MainNavigation(nav)
                }
            }
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        emitDeepLinkIntent(intent) // 포그라운드 클릭 시 여기로 옴
    }

    private fun emitDeepLinkIntent(intent: Intent) {
        Log.d("DeepLink", "emit intent: action=${intent.action}, extras=${intent.extras}")
        // 실제 딥링크 정보가 없으면 패스
        val hasInfo = intent.hasExtra("deeplink_type") && intent.hasExtra("groupId")
        if (hasInfo) {
            // 최신 값 push (버퍼가 있으면 drop 되지 않음)
            MainActivity.deepLinkIntents.tryEmit(intent)
        }
    }

    // 앱이 완전히 종료된 상태에서 알림을 눌렀을 때의 처리
    private fun handleInitialDeepLink(intent: Intent, navController: NavHostController) {
        val deeplinkType = intent.getStringExtra("deeplink_type")
        val groupId = intent.getStringExtra("groupId")?.toLongOrNull()

        Log.d("DeepLink", "초기 딥링크 처리 - type=$deeplinkType, groupId=$groupId")

        if (groupId == null || groupId == -1L) return

        // 스플래시 화면을 거치지 않고 직접 해당 화면으로 이동
        when (deeplinkType) {
            "chat" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("groupId", groupId)
                navController.navigate(Screen.ChatRoomScreen.route) {
                    launchSingleTop = true
                }
            }
            "group_detail" -> {
                navController.currentBackStackEntry?.savedStateHandle?.set("groupId", groupId)
                navController.currentBackStackEntry?.savedStateHandle?.set("isMyGroup", true)
                navController.navigate(Screen.GroupDetailScreen.route) {
                    launchSingleTop = true
                }
            }
        }

        // 처리 후 Intent 정리
        intent.removeExtra("deeplink_type")
        intent.removeExtra("groupId")
    }
}

