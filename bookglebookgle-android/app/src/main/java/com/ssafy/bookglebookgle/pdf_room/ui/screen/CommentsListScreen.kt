package com.ssafy.bookglebookgle.pdf_room.ui.screen

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf_room.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf_room.state.ResponseState
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf_room.viewmodel.CommentsListViewModel

// Font families
private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))
private val JakartaSansLight = FontFamily(Font(R.font.jakarta_sans_light_300, FontWeight.Light))
private val JakartaSansMedium = FontFamily(Font(R.font.jakarta_sans_medium_500, FontWeight.Medium))

enum class CommentsScreenMode {
    IDLE,
    SELECTION_MODE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsListScreen(
    initialComments: List<CommentModel>,
    onBackPressed: (deletedCommentIds: List<Long>) -> Unit,
    onCommentClicked: (CommentModel, deletedCommentIds: List<Long>) -> Unit,
    viewModel: CommentsListViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // States
    var comments by remember { mutableStateOf(initialComments.toMutableList()) }
    var screenMode by remember { mutableStateOf(CommentsScreenMode.IDLE) }
    var deletedCommentIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedComments by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Observe ViewModel state
    val deleteCommentState by viewModel.deleteCommentResponse.state.observeAsState()

    // Handle delete comment state changes
    LaunchedEffect(deleteCommentState) {
        deleteCommentState?.let { state ->
            when (state) {
                is ResponseState.Success<*> -> {
                    val response = state.response as? DeleteAnnotationResponse
                    response?.let {
                        deletedCommentIds = deletedCommentIds + response.deletedIds
                        comments.removeAll { comment -> response.deletedIds.contains(comment.id) }
                        selectedComments = emptySet()
                        screenMode = CommentsScreenMode.IDLE
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
        if (screenMode == CommentsScreenMode.SELECTION_MODE) {
            selectedComments = emptySet()
            screenMode = CommentsScreenMode.IDLE
        } else {
            onBackPressed(deletedCommentIds)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Comments",
                    fontSize = 20.sp,
                    fontFamily = JakartaSansSemiBold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (screenMode == CommentsScreenMode.SELECTION_MODE) {
                            selectedComments = emptySet()
                            screenMode = CommentsScreenMode.IDLE
                        } else {
                            onBackPressed(deletedCommentIds)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (screenMode == CommentsScreenMode.SELECTION_MODE) {
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
                if (screenMode == CommentsScreenMode.SELECTION_MODE) {
                    IconButton(
                        onClick = {
                            val idsToDelete = selectedComments.map { index -> comments[index].id }
                            viewModel.deleteComments(idsToDelete)
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
            if (comments.isEmpty()) {
                Text(
                    text = "No Comments found",
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
                    itemsIndexed(comments) { index, comment ->
                        CommentListItem(
                            comment = comment,
                            isSelected = selectedComments.contains(index),
                            screenMode = screenMode,
                            onClick = {
                                when (screenMode) {
                                    CommentsScreenMode.IDLE -> {
                                        onCommentClicked(comment, deletedCommentIds)
                                    }
                                    CommentsScreenMode.SELECTION_MODE -> {
                                        selectedComments = if (selectedComments.contains(index)) {
                                            val newSelection = selectedComments - index
                                            if (newSelection.isEmpty()) {
                                                screenMode = CommentsScreenMode.IDLE
                                            }
                                            newSelection
                                        } else {
                                            selectedComments + index
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                selectedComments = selectedComments + index
                                screenMode = CommentsScreenMode.SELECTION_MODE
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentListItem(
    comment: CommentModel,
    isSelected: Boolean,
    screenMode: CommentsScreenMode,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Snippet
                Text(
                    text = comment.snippet,
                    fontSize = 15.sp,
                    fontFamily = JakartaSansLight,
                    color = Color.Gray,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Updated At and Page
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = com.ssafy.bookglebookgle.pdf_room.tools.DateTimeFormatter.format(
                            comment.updatedAt,
                            com.ssafy.bookglebookgle.pdf_room.tools.DateTimeFormatter.DATE_AND_TIME_THREE
                        ),
                        fontSize = 15.sp,
                        fontFamily = JakartaSansRegular,
                        color = Color.Gray
                    )

                    Text(
                        text = "Page" + comment.page,
                        fontSize = 15.sp,
                        fontFamily = JakartaSansRegular,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Comment Text
                Text(
                    text = comment.text,
                    fontSize = 16.sp,
                    fontFamily = JakartaSansMedium,
                    color = Color.Black
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