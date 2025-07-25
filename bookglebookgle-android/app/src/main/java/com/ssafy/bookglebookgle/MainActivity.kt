package com.ssafy.bookglebookgle

import android.os.Build
import com.ssafy.bookglebookgle.ui.screen.LoginScreen

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import com.google.accompanist.navigation.animation.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.ssafy.bookglebookgle.ui.screen.SplashScreen
import com.ssafy.bookglebookgle.ui.theme.BookgleBookgleTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ssafy.bookglebookgle.ui.screen.MainScreen
import com.ssafy.bookglebookgle.ui.screen.RegisterScreen


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            installSplashScreen().setKeepOnScreenCondition { false }
        }

        setContent {
            BookgleBookgleTheme {
                val navController = rememberAnimatedNavController()
                Surface(modifier = Modifier.fillMaxWidth( ), color = MaterialTheme.colorScheme.background) {
                    AnimatedNavHost(
                        navController = navController,
                        startDestination = "splash",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }



                    ) {
                        composable("splash") { SplashScreen(navController) }
                        composable("login") { LoginScreen(navController) }
                        composable("main") { MainScreen(navController)}
                        composable("register"){ RegisterScreen(navController) }
                    }


                }

            }
        }
    }
}