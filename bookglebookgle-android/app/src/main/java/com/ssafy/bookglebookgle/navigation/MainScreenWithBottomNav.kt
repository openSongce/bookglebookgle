package com.ssafy.bookglebookgle.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pdfnotemate.ui.activity.home.HomeScreen
import com.ssafy.bookglebookgle.ui.screen.MainScreen

@Composable
fun MainScreenWithBottomNav() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                selectedRoute = currentRoute,
                onItemSelected = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(BottomNavItem.Home.route) {
//                                inclusive = false
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route) {
                MainScreen(navController)
            }
            composable(BottomNavItem.Chat.route) {
//                ChatScreen(navController)
            }
            composable(BottomNavItem.Meeting.route) {
//                MeetingScreen(navController)
            }
            composable(BottomNavItem.Profile.route) {
                val currentEntry = navController.currentBackStackEntry
                val pdfDeleted = currentEntry?.savedStateHandle?.get<Boolean>(NavKeys.PDF_DELETED) ?: false

                // Listen for PDF addition result
                val pdfAdded = currentEntry?.savedStateHandle?.get<Boolean>(NavKeys.PDF_ADDED) ?: false

                HomeScreen(
                    onBackPressed = {
                    },
                    onNavigateToAddPdf = { pageType ->
                        navController.currentBackStackEntry?.savedStateHandle?.set(NavKeys.PAGE_TYPE, pageType)
                        navController.navigate(NavRoutes.ADD_PDF)
                    },
                    onNavigateToPdfReader = { pdfDetails ->
                        navController.currentBackStackEntry?.savedStateHandle?.set(NavKeys.PDF_DETAILS, pdfDetails)
                        navController.navigate(NavRoutes.PDF_READER)
                    },
                    onPdfAdded = {
                        if (pdfAdded) {
                            navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>(NavKeys.PDF_ADDED)
                        }
                    },
                    onPdfDeleted = {
                        if (pdfDeleted) {
                            navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>(NavKeys.PDF_DELETED)
                        }
                    }
                )
            }
        }
    }
}
