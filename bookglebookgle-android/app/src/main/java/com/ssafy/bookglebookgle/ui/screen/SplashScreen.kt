package com.ssafy.bookglebookgle.ui.screen


import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.viewmodel.SplashViewModel
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.ui.theme.MainColor

@SuppressLint("ContextCastToActivity")
@Composable
fun SplashScreen(navController: NavController) {
    val vm: SplashViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 자동 로그인 시도 (한 번만)
    LaunchedEffect(Unit) {
        vm.autoLogin()
    }

    LaunchedEffect(state) {
        when (state) {
            is SplashViewModel.UiState.GoMain -> {
                navController.navigate(Screen.MainScreen.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true }
                }
            }
            is SplashViewModel.UiState.GoLogin -> {
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo(Screen.SplashScreen.route) { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    // 스플래시 UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bookgle_final_icon),
            contentDescription = "BookgleBookgle Logo",
            modifier = Modifier
                .fillMaxWidth(0.42f)   // 화면 너비의 42% (원하는 비율로 조절)
                .aspectRatio(1f)       // 정사각형 유지
                .clip(CircleShape),    // 여기! 동그랗게
            contentScale = ContentScale.Crop // 원 안을 꽉 채우도록
        )
    }


}
