package com.ssafy.bookglebookgle.navigation

// 모든 화면 라우트 관리
sealed class Screen(val route: String) {
    object SplashScreen : Screen("splash")
    object LoginScreen : Screen("login")
    object RegisterScreen : Screen("register")
    object MainScreen : Screen("main")
    object AddPdfScreen : Screen("add_pdf")
    object PdfReaderScreen : Screen("pdf_reader")
    object CommentsListScreen : Screen("comments_list")
    object HighlightsListScreen : Screen("highlights_list")
    object BookmarksListScreen : Screen("bookmarks_list")
    object PdfReadScreen : Screen("pdf_read")
    object GroupRegisterScreen : Screen("group_register")
    object GroupDetailScreen : Screen("group_detail")
    object ChatRoomScreen : Screen("chat_room")
    object MyBookShelfScreen : Screen("my_book_shelf")
}

// 화면간에 데이터를 전달하기위한 키
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
    const val USER_ID = "userId"
}