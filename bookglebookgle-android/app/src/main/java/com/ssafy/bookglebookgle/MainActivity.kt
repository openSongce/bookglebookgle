package com.ssafy.bookglebookgle

import com.ssafy.bookglebookgle.ui.screen.LoginScreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssafy.bookglebookgle.ui.theme.BookgleBookgleTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BookgleBookgleTheme {
                val navController = rememberNavController()
                Surface(modifier = Modifier.fillMaxWidth( ), color = MaterialTheme.colorScheme.background) {
                    NavHost(navController = navController, startDestination = "login"){
                        composable("login"){
                            LoginScreen(navController)
                        }
                    }
                }

            }
        }
    }
}