package com.ssafy.bookglebookgle.pdf.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf.state.ResponseState
import com.ssafy.bookglebookgle.pdf.tools.DateTimeFormatter
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.viewmodel.BookmarkListViewModel

// Font families
private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))

enum class BookmarkScreenMode {
    IDLE,
    SELECTION_MODE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScreen(
    initialBookmarks: List<BookmarkModel>,
    onBackPressed: (deletedBookmarkIds: List<Long>) -> Unit,
    onBookmarkClicked: (BookmarkModel, deletedBookmarkIds: List<Long>) -> Unit,
    viewModel: BookmarkListViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // States
    var bookmarks by remember { mutableStateOf(initialBookmarks.toMutableList()) }
    var screenMode by remember { mutableStateOf(BookmarkScreenMode.IDLE) }
    var deletedBookmarkIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedBookmarks by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Observe ViewModel state
    val deleteBookmarkState by viewModel.deleteBookmarksResponse.state.observeAsState()

    // Handle delete bookmark state changes
    LaunchedEffect(deleteBookmarkState) {
        deleteBookmarkState?.let { state ->
            when (state) {
                is ResponseState.Success<*> -> {
                    val response = state.response as? DeleteAnnotationResponse
                    response?.let {
                        deletedBookmarkIds = deletedBookmarkIds + response.deletedIds
                        bookmarks.removeAll { bookmark -> response.deletedIds.contains(bookmark.id) }
                        selectedBookmarks = emptySet()
                        screenMode = BookmarkScreenMode.IDLE
                    }
                }
                is ResponseState.Failed -> {
                    // Show error message
                }
                else -> {}
            }
        }
    }

    // Handle back press
    BackHandler {
        if (screenMode == BookmarkScreenMode.SELECTION_MODE) {
            selectedBookmarks = emptySet()
            screenMode = BookmarkScreenMode.IDLE
        } else {
            onBackPressed(deletedBookmarkIds)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Bookmarks",
                    fontSize = 20.sp,
                    fontFamily = JakartaSansSemiBold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (screenMode == BookmarkScreenMode.SELECTION_MODE) {
                            selectedBookmarks = emptySet()
                            screenMode = BookmarkScreenMode.IDLE
                        } else {
                            onBackPressed(deletedBookmarkIds)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (screenMode == BookmarkScreenMode.SELECTION_MODE) {
                                R.drawable.ic_close_white
                            } else {
                                R.drawable.ic_arrow_back
                            }
                        ),
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                if (screenMode == BookmarkScreenMode.SELECTION_MODE) {
                    IconButton(
                        onClick = {
                            val idsToDelete = selectedBookmarks.map { index -> bookmarks[index].id }
                            viewModel.deleteBookmarks(idsToDelete)
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Red
            )
        )

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            if (bookmarks.isEmpty()) {
                Text(
                    text = "No Bookmarks found",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 16.sp,
                    fontFamily = JakartaSansSemiBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(bookmarks) { index, bookmark ->
                        BookmarkListItem(
                            bookmark = bookmark,
                            isSelected = selectedBookmarks.contains(index),
                            screenMode = screenMode,
                            onClick = {
                                when (screenMode) {
                                    BookmarkScreenMode.IDLE -> {
                                        onBookmarkClicked(bookmark, deletedBookmarkIds)
                                    }
                                    BookmarkScreenMode.SELECTION_MODE -> {
                                        selectedBookmarks = if (selectedBookmarks.contains(index)) {
                                            val newSelection = selectedBookmarks - index
                                            if (newSelection.isEmpty()) {
                                                screenMode = BookmarkScreenMode.IDLE
                                            }
                                            newSelection
                                        } else {
                                            selectedBookmarks + index
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                selectedBookmarks = selectedBookmarks + index
                                screenMode = BookmarkScreenMode.SELECTION_MODE
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkListItem(
    bookmark: BookmarkModel,
    isSelected: Boolean,
    screenMode: BookmarkScreenMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .alpha(if (isSelected) 0.5f else 1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(5.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Updated At
                Text(
                    text = DateTimeFormatter.format(
                        bookmark.updatedAt,
                        DateTimeFormatter.DATE_AND_TIME_THREE
                    ),
                    fontSize = 15.sp,
                    fontFamily = JakartaSansRegular,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )

                // Page
                Text(
                    text = "Page" + bookmark.page,
                    fontSize = 15.sp,
                    fontFamily = JakartaSansRegular,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }

            // Selection indicator
            if (isSelected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_selected),
                    contentDescription = "Selected",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(35.dp),
                    tint = Color.Red
                )
            }
        }
    }
}