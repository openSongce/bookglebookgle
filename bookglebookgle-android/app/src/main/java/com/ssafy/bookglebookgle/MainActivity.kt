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
import com.kakao.sdk.common.util.Utility
import com.ssafy.bookglebookgle.navigation.MainNavigation
import android.content.Intent
import androidx.activity.ComponentActivity

private const val TAG = "싸피_MainActivity"
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_BookgleBookgle)

        // 시스템 스플래시를 최대한 빨리 제거
        val splashScreen = installSplashScreen()

        // 즉시 스플래시 제거
        splashScreen.setKeepOnScreenCondition { false }

        val keyHash = Utility.getKeyHash(this)
        Log.d(TAG, "keyHash : $keyHash")

        handleKakaoRedirect(intent)

        enableEdgeToEdge()
        setContent {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleKakaoRedirect(intent)
    }

    private fun handleKakaoRedirect(intent: android.content.Intent?) {
        val uri = intent?.data ?: return

        Log.d(TAG, "카카오 리다이렉트 URI 수신: $uri")

        // Kakao SDK에 이미 accessToken이 있는 경우 확인
        com.kakao.sdk.user.UserApiClient.instance.accessTokenInfo { tokenInfo, error ->
            if (tokenInfo != null) {
                Log.d(TAG, "카카오 accessToken 유효함. userId: ${tokenInfo.id}")
                // 여기서 ViewModel 등을 이용해 서버 로그인 처리 시작 가능
            } else {
                Log.e(TAG, "카카오 accessToken 유효하지 않음. 재로그인 필요", error)
            }
        }
    }


}
