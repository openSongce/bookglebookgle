package com.ssafy.bookglebookgle.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pdfnotemate.ui.activity.home.HomeScreen
import com.example.pdfnotemate.ui.activity.reader.PdfReaderScreen
import com.ssafy.bookglebookgle.pdf_room.response.PdfNoteListModel
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf_room.ui.screen.AddPdfPageType
import com.ssafy.bookglebookgle.pdf_room.ui.screen.AddPdfScreen
import com.ssafy.bookglebookgle.pdf_room.ui.screen.BookmarkListScreen
import com.ssafy.bookglebookgle.pdf_room.ui.screen.CommentsListScreen
import com.ssafy.bookglebookgle.pdf_room.ui.screen.EntryScreen
import com.ssafy.bookglebookgle.pdf_room.ui.screen.HighlightListScreen

// Navigation routes
object NavRoutes {
    const val ENTRY = "entry"
    const val HOME = "home"
    const val ADD_PDF = "add_pdf"
    const val PDF_READER = "pdf_reader"
    const val COMMENTS_LIST = "comments_list"
    const val HIGHLIGHTS_LIST = "highlights_list"
    const val BOOKMARKS_LIST = "bookmarks_list"
}

// Keys for passing data between screens
object NavKeys {
    const val PDF_DETAILS = "pdf_details"
    const val PDF_ADDED = "pdf_added"
    const val PDF_DELETED = "pdf_deleted"
    const val PAGE_TYPE = "page_type"
    const val COMMENTS = "comments"
    const val HIGHLIGHTS = "highlights"
    const val BOOKMARKS = "bookmarks"
    const val DELETED_COMMENT_IDS = "deleted_comment_ids"
    const val DELETED_HIGHLIGHT_IDS = "deleted_highlight_ids"
    const val DELETED_BOOKMARK_IDS = "deleted_bookmark_ids"
    const val SELECTED_COMMENT = "selected_comment"
    const val SELECTED_HIGHLIGHT = "selected_highlight"
    const val SELECTED_BOOKMARK = "selected_bookmark"
}

@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.ENTRY
    ) {
        // Entry/Splash Screen
        composable(NavRoutes.ENTRY) {
            EntryScreen(
                onStartClicked = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.ENTRY) { inclusive = true }
                    }
                }
            )
        }

        // Home Screen
        composable(NavRoutes.HOME) {
            // Listen for PDF deletion result
            val currentEntry = navController.currentBackStackEntry
            val pdfDeleted = currentEntry?.savedStateHandle?.get<Boolean>(NavKeys.PDF_DELETED) ?: false

            // Listen for PDF addition result
            val pdfAdded = currentEntry?.savedStateHandle?.get<Boolean>(NavKeys.PDF_ADDED) ?: false

            HomeScreen(
                onBackPressed = {
                    // Exit app or handle back navigation
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
                    // This will be triggered when returning from AddPdfScreen
                    if (pdfAdded) {
                        navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>(NavKeys.PDF_ADDED)
                    }
                },
                onPdfDeleted = {
                    // This will be triggered when returning from PdfReaderScreen
                    if (pdfDeleted) {
                        navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>(NavKeys.PDF_DELETED)
                    }
                }
            )
        }

        // Add PDF Screen
        composable(NavRoutes.ADD_PDF) {
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

        // PDF Reader Screen
        composable(NavRoutes.PDF_READER) {
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

        // Comments List Screen
        composable(NavRoutes.COMMENTS_LIST) {
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

        // Highlights List Screen
        composable(NavRoutes.HIGHLIGHTS_LIST) {
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

        // Bookmarks List Screen
        composable(NavRoutes.BOOKMARKS_LIST) {
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