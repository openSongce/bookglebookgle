package com.ssafy.bookglebookgle.ui.screen


import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ssafy.bookglebookgle.navigation.Screen
import com.ssafy.bookglebookgle.viewmodel.SplashViewModel

@Composable
fun SplashScreen(navController: NavController) {
    val vm: SplashViewModel = hiltViewModel()

    LaunchedEffect(Unit) { vm.autoLogin() }   // 자동 로그인 시도

    when (val state = vm.uiState.collectAsState().value) {
        SplashViewModel.UiState.Loading -> {

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
