package com.ssafy.bookglebookgle.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pdfnotemate.ui.activity.home.PdfScreen
import com.example.pdfnotemate.ui.activity.reader.PdfReaderScreen
import com.ssafy.bookglebookgle.pdf.response.PdfNoteListModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf.ui.screen.AddPdfPageType
import com.ssafy.bookglebookgle.pdf.ui.screen.AddPdfScreen
import com.ssafy.bookglebookgle.pdf.ui.screen.BookmarkListScreen
import com.ssafy.bookglebookgle.pdf.ui.screen.CommentsListScreen
import com.ssafy.bookglebookgle.pdf.ui.screen.HighlightListScreen
import com.ssafy.bookglebookgle.ui.screen.GroupRegisterScreen
import com.ssafy.bookglebookgle.ui.screen.LoginScreen
import com.ssafy.bookglebookgle.ui.screen.MainScreen
import com.ssafy.bookglebookgle.ui.screen.MyGroupScreen
import com.ssafy.bookglebookgle.ui.screen.ProfileScreen
import com.ssafy.bookglebookgle.ui.screen.RegisterScreen
import com.ssafy.bookglebookgle.ui.screen.SplashScreen

@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController()
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // 바텀 네비게이션을 표시할 화면들
    val screensWithBottomNav = BottomNavItem.items.map { it.route }

    Scaffold(
        bottomBar = {
            // 바텀 네비게이션이 필요한 화면에서만 표시
            if (currentRoute in screensWithBottomNav) {
                BottomNavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    selectedRoute = currentRoute,
                    onItemSelected = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(BottomNavItem.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.SplashScreen.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.SplashScreen.route) {
                SplashScreen(navController)
            }

            composable(Screen.MainScreen.route){
                MainScreen(navController)
            }

            composable(Screen.LoginScreen.route) {
                LoginScreen(navController)
            }

            composable(Screen.RegisterScreen.route) {
                RegisterScreen(navController)
            }

            composable(BottomNavItem.Home.route) {
                MainScreen(navController)
            }

            composable(BottomNavItem.Chat.route) {
                // ChatScreen(navController)
            }

            composable(BottomNavItem.MyGroup.route) {
                MyGroupScreen(navController)
            }

            composable(BottomNavItem.Profile.route) {
                ProfileScreen(navController)
            }

            composable(Screen.GroupRegisterScreen.route){
                GroupRegisterScreen(navController)
            }

            composable(Screen.AddPdfScreen.route) {
                val pageType = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<AddPdfPageType>(NavKeys.PAGE_TYPE) ?: AddPdfPageType.PickFromGallery

                AddPdfScreen(
                    pageType = pageType,
                    onBackPressed = {
                        navController.navigateUp()
                    },
                    onPdfAdded = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(NavKeys.PDF_ADDED, true)
                        navController.navigateUp()
                    }
                )
            }

            composable(Screen.PdfReaderScreen.route) {
                val pdfDetails = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<PdfNoteListModel>(NavKeys.PDF_DETAILS) ?: return@composable

                PdfReaderScreen(
                    pdfDetails = pdfDetails,
                    onBackPressed = {
                        navController.navigateUp()
                    },
                    onPdfDeleted = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(NavKeys.PDF_DELETED, true)
                        navController.navigateUp()
                    }
                )
            }

            composable(Screen.CommentsListScreen.route) {
                val comments = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<List<CommentModel>>(NavKeys.COMMENTS) ?: emptyList()

                CommentsListScreen(
                    initialComments = comments,
                    onBackPressed = { deletedIds ->
                        if (deletedIds.isNotEmpty()) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(NavKeys.DELETED_COMMENT_IDS, deletedIds)
                        }
                        navController.navigateUp()
                    },
                    onCommentClicked = { comment, deletedIds ->
                        if (deletedIds.isNotEmpty()) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(NavKeys.DELETED_COMMENT_IDS, deletedIds)
                        }
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(NavKeys.SELECTED_COMMENT, comment)
                        navController.navigateUp()
                    }
                )
            }

            composable(Screen.HighlightsListScreen.route) {
                val highlights = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<List<HighlightModel>>(NavKeys.HIGHLIGHTS) ?: emptyList()

                HighlightListScreen(
                    initialHighlights = highlights,
                    onBackPressed = { deletedIds ->
                        if (deletedIds.isNotEmpty()) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(NavKeys.DELETED_HIGHLIGHT_IDS, deletedIds)
                        }
                        navController.navigateUp()
                    },
                    onHighlightClicked = { highlight, deletedIds ->
                        if (deletedIds.isNotEmpty()) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(NavKeys.DELETED_HIGHLIGHT_IDS, deletedIds)
                        }
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(NavKeys.SELECTED_HIGHLIGHT, highlight)
                        navController.navigateUp()
                    }
                )
            }

            composable(Screen.BookmarksListScreen.route) {
                val bookmarks = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<List<BookmarkModel>>(NavKeys.BOOKMARKS) ?: emptyList()

                BookmarkListScreen(
                    initialBookmarks = bookmarks,
                    onBackPressed = { deletedIds ->
                        if (deletedIds.isNotEmpty()) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(NavKeys.DELETED_BOOKMARK_IDS, deletedIds)
                        }
                        navController.navigateUp()
                    },
                    onBookmarkClicked = { bookmark, deletedIds ->
                        if (deletedIds.isNotEmpty()) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(NavKeys.DELETED_BOOKMARK_IDS, deletedIds)
                        }
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(NavKeys.SELECTED_BOOKMARK, bookmark)
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}