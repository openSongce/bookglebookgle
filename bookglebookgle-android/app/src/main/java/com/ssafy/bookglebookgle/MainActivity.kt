package com.ssafy.bookglebookgle

import android.os.Build
import com.ssafy.bookglebookgle.ui.screen.LoginScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.ssafy.bookglebookgle.ui.screen.SplashScreen
import com.ssafy.bookglebookgle.ui.theme.BookgleBookgleTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ssafy.bookglebookgle.navigation.MainScreenWithBottomNav
import com.ssafy.bookglebookgle.ui.screen.RegisterScreen


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen().setKeepOnScreenCondition { false }
        }

        setContent {
            BookgleBookgleTheme {
                val navController = rememberNavController()
                Surface(modifier = Modifier.fillMaxWidth( ), color = MaterialTheme.colorScheme.background) {
                    NavHost (
                        navController = navController,
                        startDestination = "splash",

                    ) {
                        composable("splash") { SplashScreen(navController) }
                        composable("login") { LoginScreen(navController) }
                        composable("main") { MainScreenWithBottomNav() }
                        composable("register"){ RegisterScreen(navController) }
                    }


                }

            }
        }
    }
}
