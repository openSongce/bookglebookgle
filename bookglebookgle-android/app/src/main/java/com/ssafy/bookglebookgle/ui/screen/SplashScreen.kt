package com.ssafy.bookglebookgle.ui.screen


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.viewmodel.SplashViewModel

@Composable
fun SplashScreen(navController: NavController) {
    val vm: SplashViewModel = hiltViewModel()

    LaunchedEffect(Unit) { vm.autoLogin() }   // 자동 로그인 시도
    val state = vm.uiState.collectAsState().value

    when (state) {
        SplashViewModel.UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        }
        SplashViewModel.UiState.GoMain -> {
            LaunchedEffect(Unit) {
                navController.navigate(Screen.MainScreen.route) {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
        SplashViewModel.UiState.GoLogin -> {
            LaunchedEffect(Unit) {
                navController.navigate(Screen.LoginScreen.route) {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

}
