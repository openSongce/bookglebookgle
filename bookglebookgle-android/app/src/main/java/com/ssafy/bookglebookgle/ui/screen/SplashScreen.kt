package com.ssafy.bookglebookgle.ui.screen


import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                navController.navigate(Screen.MainScreen.route) {
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
            .background(MainColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.login_logo),
                contentDescription = "BookgleBookgle Logo",
                modifier = Modifier
                    .size(150.dp, 150.dp),
                contentScale = ContentScale.FillBounds
            )
        }
    }

}
