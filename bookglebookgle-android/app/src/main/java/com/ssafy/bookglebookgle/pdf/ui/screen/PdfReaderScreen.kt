//package com.example.pdfnotemate.ui.activity.reader
//
//import android.graphics.PointF
//import android.util.Log
//import android.widget.Toast
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.runtime.livedata.observeAsState
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.font.Font
//import androidx.compose.ui.text.font.FontFamily
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.hilt.navigation.compose.hiltViewModel
//import com.ssafy.bookglebookgle.R
//import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
//import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
//import com.ssafy.bookglebookgle.pdf.response.PdfNoteListModel
//import com.ssafy.bookglebookgle.pdf.state.ResponseState
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PdfFile
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.PdfAnnotationModel
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.TextSelectionData
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.selection.TextSelectionOptionsWindow
//import com.ssafy.bookglebookgle.pdf.ui.CommentAddBottomSheet
//import com.ssafy.bookglebookgle.pdf.ui.CommentEditBottomSheet
//import com.ssafy.bookglebookgle.pdf.ui.CommentPageType
//import com.ssafy.bookglebookgle.pdf.ui.CommentViewBottomSheet
//import com.ssafy.bookglebookgle.pdf.ui.MoreOptionModel
//import com.ssafy.bookglebookgle.pdf.ui.OptionPickBottomSheet
//import com.ssafy.bookglebookgle.pdf.ui.screen.BookmarkListScreen
//import com.ssafy.bookglebookgle.pdf.ui.screen.CommentsListScreen
//import com.ssafy.bookglebookgle.pdf.ui.screen.HighlightListScreen
//import com.ssafy.bookglebookgle.pdf.viewmodel.PdfReaderViewModel
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import java.io.File
//
//// Font families
//private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))
//
//@Composable
//fun PdfReaderScreen(
//    pdfDetails: PdfNoteListModel,
//    onBackPressed: () -> Unit,
//    onPdfDeleted: () -> Unit,
//    viewModel: PdfReaderViewModel = hiltViewModel()
//) {
//    val context = LocalContext.current
//
//    // States
//    var isLoading by remember { mutableStateOf(true) }
//    var showMoreOptions by remember { mutableStateOf(false) }
//    var showCommentBottomSheet by remember { mutableStateOf(false) }
//    var currentCommentModel by remember { mutableStateOf<CommentModel?>(null) }
//    var commentPageType by remember { mutableStateOf(CommentPageType.VIEW) }
//    var currentPage by remember { mutableStateOf(1) }
//    var totalPages by remember { mutableStateOf(1) }
//    var isBookmarked by remember { mutableStateOf(false) }
//    var showPageInfo by remember { mutableStateOf(false) }
//
//    // Navigation states for lists
//    var showCommentsList by remember { mutableStateOf(false) }
//    var showHighlightsList by remember { mutableStateOf(false) }
//    var showBookmarksList by remember { mutableStateOf(false) }
//    var commentsToShow by remember { mutableStateOf<List<CommentModel>>(emptyList()) }
//    var highlightsToShow by remember { mutableStateOf<List<HighlightModel>>(emptyList()) }
//    var bookmarksToShow by remember { mutableStateOf<List<BookmarkModel>>(emptyList()) }
//
//    // PDF View and related components
//    var pdfView by remember { mutableStateOf<PDFView?>(null) }
//    var textSelectionWindow by remember { mutableStateOf<TextSelectionOptionsWindow?>(null) }
//    val pdfRenderScope = remember { CoroutineScope(Dispatchers.IO) }
//
//    // Initialize ViewModel with PDF details
//    LaunchedEffect(pdfDetails) {
//        viewModel.pdfDetails = pdfDetails
//    }
//
//    // Observe ViewModel states
//    val addCommentState by viewModel.addCommentResponse.state.observeAsState()
//    val addHighlightState by viewModel.addHighlightResponse.state.observeAsState()
//    val addBookmarkState by viewModel.addBookmarkResponse.state.observeAsState()
//    val removeBookmarkState by viewModel.removeBookmarkResponse.state.observeAsState()
//    val annotationListState by viewModel.annotationListResponse.state.observeAsState()
//    val updateCommentState by viewModel.updateCommentResponse.state.observeAsState()
//    val deleteCommentState by viewModel.deleteCommentResponse.state.observeAsState()
//    val pdfDeleteState by viewModel.pdfDeleteResponse.state.observeAsState()
//
//    // Handle state changes
//    LaunchedEffect(addCommentState) {
//        addCommentState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    val response = state.response as? CommentModel
//                    response?.let {
//                        viewModel.annotations.comments.add(0, it)
//                        pdfView?.addComment(it)
//                    }
//                    // Show success message
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    LaunchedEffect(addHighlightState) {
//        addHighlightState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    val response = state.response as? HighlightModel
//                    response?.let {
//                        viewModel.annotations.highlights.add(0, it)
//                        pdfView?.addHighlight(it)
//                    }
//                    // Show success message
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    LaunchedEffect(addBookmarkState) {
//        addBookmarkState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    val response = state.response as? BookmarkModel
//                    response?.let {
//                        viewModel.annotations.bookmarks.add(it)
//                        isBookmarked = viewModel.annotations.bookmarks.any { bookmark -> bookmark.page == currentPage }
//                    }
//                    // Show success message
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    LaunchedEffect(removeBookmarkState) {
//        removeBookmarkState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    val response = state.response as? DeleteAnnotationResponse
//                    response?.let {
//                        viewModel.annotations.bookmarks.removeAll { bookmark ->
//                            response.deletedIds.contains(bookmark.id)
//                        }
//                        isBookmarked = viewModel.annotations.bookmarks.any { bookmark -> bookmark.page == currentPage }
//                    }
//                    // Show success message
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    LaunchedEffect(annotationListState) {
//        annotationListState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    val response = state.response as? AnnotationListResponse
//                    response?.let {
//                        viewModel.annotations = response
//                        val highlightAndComments = arrayListOf<PdfAnnotationModel>()
//                        highlightAndComments.addAll(response.comments)
//                        highlightAndComments.addAll(response.highlights)
//                        pdfView?.loadAnnotations(highlightAndComments)
//                    }
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    LaunchedEffect(updateCommentState) {
//        updateCommentState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    val response = state.response as? CommentModel
//                    response?.let {
//                        viewModel.annotations.comments.map { comment ->
//                            if (comment.id == response.id) {
//                                comment.text = response.text
////                                comment.updatedAt = response.updatedAt
//                            }
//                        }
//                        pdfView?.updateComment(response)
//                    }
//                    // Show success message
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    LaunchedEffect(deleteCommentState) {
//        deleteCommentState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    val response = state.response as? DeleteAnnotationResponse
//                    response?.let {
//                        viewModel.removeComments(response.deletedIds)
//                        pdfView?.removeCommentAnnotations(response.deletedIds)
//                    }
//                    // Show success message
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    LaunchedEffect(pdfDeleteState) {
//        pdfDeleteState?.let { state ->
//            when (state) {
//                is ResponseState.Success<*> -> {
//                    onPdfDeleted()
//                }
//                is ResponseState.Failed -> {
//                    // Show error message
//                }
//                else -> {}
//            }
//        }
//    }
//
//    // List screens (render on top when shown)
//    if (showCommentsList) {
//        CommentsListScreen(
//            initialComments = commentsToShow,
//            onBackPressed = { deletedIds ->
//                if (deletedIds.isNotEmpty()) {
//                    viewModel.removeComments(deletedIds)
//                    pdfView?.removeCommentAnnotations(deletedIds)
//                }
//                showCommentsList = false
//            },
//            onCommentClicked = { comment, deletedIds ->
//                if (deletedIds.isNotEmpty()) {
//                    viewModel.removeComments(deletedIds)
//                    pdfView?.removeCommentAnnotations(deletedIds)
//                }
//                pdfView?.jumpTo(comment.page - 1)
//                currentCommentModel = comment
//                commentPageType = CommentPageType.VIEW
//                showCommentBottomSheet = true
//                showCommentsList = false
//            }
//        )
//    } else if (showHighlightsList) {
//        HighlightListScreen(
//            initialHighlights = highlightsToShow,
//            onBackPressed = { deletedIds ->
//                if (deletedIds.isNotEmpty()) {
//                    viewModel.removeHighlight(deletedIds)
//                    pdfView?.removeHighlightAnnotations(deletedIds)
//                }
//                showHighlightsList = false
//            },
//            onHighlightClicked = { highlight, deletedIds ->
//                if (deletedIds.isNotEmpty()) {
//                    viewModel.removeHighlight(deletedIds)
//                    pdfView?.removeHighlightAnnotations(deletedIds)
//                }
//                pdfView?.jumpTo(highlight.page - 1)
//                showHighlightsList = false
//            }
//        )
//    } else if (showBookmarksList) {
//        BookmarkListScreen(
//            initialBookmarks = bookmarksToShow,
//            onBackPressed = { deletedIds ->
//                if (deletedIds.isNotEmpty()) {
//                    viewModel.removeBookmarks(deletedIds)
//                }
//                showBookmarksList = false
//            },
//            onBookmarkClicked = { bookmark, deletedIds ->
//                if (deletedIds.isNotEmpty()) {
//                    viewModel.removeBookmarks(deletedIds)
//                }
//                pdfView?.jumpTo(bookmark.page - 1)
//                showBookmarksList = false
//            }
//        )
//    } else {
//        // Main PDF Reader UI (only show when no list is open)
//        Column(
//            modifier = Modifier.fillMaxSize()
//        ) {
//            // Top Bar
//            TopAppBar(
//                title = pdfDetails.title,
//                onBackPressed = onBackPressed,
//                onMoreOptionsClick = { showMoreOptions = true }
//            )
//
//            // PDF Content
//            Box(
//                modifier = Modifier.fillMaxSize()
//            ) {
//                // PDF View
//                AndroidView(
//                    factory = { context ->
//                        PDFView(context, null).apply {
//                            pdfView = this
//                            attachCoroutineScope(pdfRenderScope)
//                            setListener(object : PDFView.Listener {
//                                override fun onPreparationStarted() {
//                                    isLoading = true
//                                }
//
//                                override fun onPreparationSuccess() {
//                                    viewModel.loadAllAnnotations()
//                                    isLoading = false
//                                    totalPages = getTotalPage()
//                                }
//
//                                override fun onPreparationFailed(error: String, e: Exception?) {
//                                    isLoading = false
//                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
//                                }
//
//                                override fun onPageChanged(pageIndex: Int, paginationPageIndex: Int) {
//                                    currentPage = pageIndex + 1
//                                    isBookmarked = viewModel.annotations.bookmarks.any { it.page == currentPage }
//                                    showPageInfo = true
//                                }
//
//                                override fun onTextSelected(selection: TextSelectionData, rawPoint: PointF) {
//                                    textSelectionWindow?.show(rawPoint.x, rawPoint.y, selection)
//                                }
//
//                                override fun hideTextSelectionOptionWindow() {
//                                    textSelectionWindow?.dismiss()
//                                }
//
//                                override fun onTextSelectionCleared() {
//                                    textSelectionWindow?.dismiss()
//                                }
//
//                                override fun onNotesStampsClicked(comments: List<CommentModel>, pointOfNote: PointF) {
//                                    if (comments.size == 1) {
//                                        currentCommentModel = comments.first()
//                                        commentPageType = CommentPageType.VIEW
//                                        showCommentBottomSheet = true
//                                    } else {
//                                        commentsToShow = comments
//                                        showCommentsList = true
//                                    }
//                                }
//
//                                override fun loadTopPdfChunk(mergeId: Int, pageIndexToLoad: Int) {}
//                                override fun loadBottomPdfChunk(mergedId: Int, pageIndexToLoad: Int) {}
//                                override fun onScrolling() {}
//                                override fun onTap() {}
//                                override fun onMergeStart(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                                override fun onMergeEnd(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                                override fun onMergeFailed(mergeId: Int, mergeType: PdfFile.MergeType, message: String, exception: java.lang.Exception?) {}
//                            })
//
//                            // Initialize text selection window
//                            textSelectionWindow = TextSelectionOptionsWindow(context, object : TextSelectionOptionsWindow.Listener {
//                                override fun onAddHighlightClick(snippet: String, color: String, page: Int, coordinates: Coordinates) {
//                                    viewModel.addHighlight(snippet, color, page, coordinates)
//                                }
//
//                                override fun onAddNotClick(snippet: String, page: Int, coordinates: Coordinates) {
////                                    val commentModel = CommentModel(-1, snippet, "", page, 0L, coordinates)
////                                    currentCommentModel = commentModel
////                                    commentPageType = CommentPageType.ADD
////                                    showCommentBottomSheet = true
//                                }
//                            })
//                            textSelectionWindow?.attachToPdfView(this)
//
//                            // Load PDF file
//                            val file = File(pdfDetails.filePath)
//                            if (file.exists()) {
//                                fromFile(file).defaultPage(0).load()
//                            } else {
//                                Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    },
//                    modifier = Modifier.fillMaxSize()
//                )
//
//                textSelectionWindow?.ComposeContent()
//
//                // Loading indicator
//                if (isLoading) {
//                    CircularProgressIndicator(
//                        modifier = Modifier.align(Alignment.Center)
//                    )
//                }
//
//                // Top right controls
//                Row(
//                    modifier = Modifier
//                        .align(Alignment.TopEnd)
//                        .padding(10.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    // Page info
//                    if (showPageInfo) {
//                        Card(
//                            modifier = Modifier.padding(end = 10.dp),
//                            colors = CardDefaults.cardColors(
//                                containerColor = Color.Gray
//                            )
//                        ) {
//                            Text(
//                                text = "$currentPage/$totalPages",
//                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
//                                fontSize = 17.sp,
//                                fontFamily = JakartaSansSemiBold,
//                                color = Color.White
//                            )
//                        }
//
//                        // Hide page info after delay
//                        LaunchedEffect(currentPage) {
//                            kotlinx.coroutines.delay(3000)
//                            showPageInfo = false
//                        }
//                    }
//
//                    // Bookmark button
//                    IconToggleButton(
//                        checked = isBookmarked,
//                        onCheckedChange = { checked ->
//                            if (checked) {
//                                viewModel.addBookmark(currentPage)
//                            } else {
//                                viewModel.removeBookmark(currentPage)
//                            }
//                        }
//                    ) {
//                        Icon(
//                            painter = painterResource(
//                                id = R.drawable.ic_bookmarked
//                            ),
//                            contentDescription = "Bookmark",
//                            tint = if (isBookmarked) Color.Yellow else Color.Gray
//                        )
//                    }
//                }
//            }
//        }
//
//        // Bottom Sheets (only show when main UI is visible)
//        if (showMoreOptions) {
//            OptionPickBottomSheet(
//                title = pdfDetails.title,
//                options = listOf(
//                    MoreOptionModel(1, "${viewModel.annotations.comments.size} Comments"),
//                    MoreOptionModel(2, "${viewModel.annotations.highlights.size} Highlights"),
//                    MoreOptionModel(3, "${viewModel.annotations.bookmarks.size} Bookmarks"),
//                    MoreOptionModel(4, "Delete this PDF")
//                ),
//                onOptionSelected = { option ->
//                    when (option.id) {
//                        1 -> {
//                            commentsToShow = viewModel.annotations.comments.toList()
//                            showCommentsList = true
//                        }
//                        2 -> {
//                            highlightsToShow = viewModel.annotations.highlights.toList()
//                            showHighlightsList = true
//                        }
//                        3 -> {
//                            bookmarksToShow = viewModel.annotations.bookmarks.toList()
//                            showBookmarksList = true
//                        }
//                        4 -> viewModel.deletePdf()
//                    }
//                },
//                onDismiss = { showMoreOptions = false }
//            )
//        }
//
//        if (showCommentBottomSheet && currentCommentModel != null) {
//            when (commentPageType) {
//                CommentPageType.VIEW -> {
//                    CommentViewBottomSheet(
//                        commentModel = currentCommentModel!!,
//                        onDeleteComment = { commentId ->
//                            viewModel.deleteComment(commentId)
//                        },
//                        onEditCommentSave = { updatedComment ->
//                            viewModel.updateComment(updatedComment.id, updatedComment.text)
//                        },
//                        onDismiss = { showCommentBottomSheet = false }
//                    )
//                }
//                CommentPageType.ADD -> {
//                    CommentAddBottomSheet(
//                        commentModel = currentCommentModel!!,
//                        onAddCommentSave = { newComment ->
//                            textSelectionWindow?.dismiss(true)
//                            viewModel.addComment(
//                                newComment.snippet,
//                                newComment.text,
//                                newComment.page,
//                                newComment.coordinates
//                            )
//                        },
//                        onDismiss = { showCommentBottomSheet = false }
//                    )
//                }
//                CommentPageType.EDIT -> {
//                    CommentEditBottomSheet(
//                        commentModel = currentCommentModel!!,
//                        onEditCommentSave = { updatedComment ->
//                            viewModel.updateComment(updatedComment.id, updatedComment.text)
//                        },
//                        onDismiss = { showCommentBottomSheet = false }
//                    )
//                }
//            }
//        }
//    }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//private fun TopAppBar(
//    title: String,
//    onBackPressed: () -> Unit,
//    onMoreOptionsClick: () -> Unit
//) {
//    TopAppBar(
//        title = {
//            Text(
//                text = title,
//                fontSize = 20.sp,
//                fontFamily = JakartaSansSemiBold,
//                color = Color.White
//            )
//        },
//        navigationIcon = {
//            IconButton(onClick = onBackPressed) {
//                Icon(
//                    painter = painterResource(id = R.drawable.ic_arrow_back),
//                    contentDescription = "Back",
//                    tint = Color.White
//                )
//            }
//        },
//        actions = {
//            IconButton(onClick = onMoreOptionsClick) {
//                Icon(
//                    painter = painterResource(id = R.drawable.ic_more),
//                    contentDescription = "More options",
//                    tint = Color.White
//                )
//            }
//        },
//        colors = TopAppBarDefaults.topAppBarColors(
//            containerColor = Color.Red
//        )
//    )
//}