package com.ssafy.bookglebookgle.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel

// Font families
private val JakartaSansBold = FontFamily(Font(R.font.jakarta_sans_bold_700, FontWeight.Bold))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))
private val JakartaSansLight = FontFamily(Font(R.font.jakarta_sans_light_300, FontWeight.Light))
private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))

enum class CommentPageType {
    VIEW, ADD, EDIT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentViewBottomSheet(
    commentModel: CommentModel,
    pageType: CommentPageType = CommentPageType.VIEW,
    onDeleteComment: (Long) -> Unit,
    onEditCommentSave: (CommentModel) -> Unit,
    onAddCommentSave: (CommentModel) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPageType by remember { mutableStateOf(pageType) }
    var commentText by remember { mutableStateOf(commentModel.text) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Red)
                    .padding(horizontal = 15.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comment",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontFamily = JakartaSansBold,
                    color = Color.White
                )

                // Action buttons based on page type
                when (currentPageType) {
                    CommentPageType.VIEW -> {
                        IconButton(
                            onClick = {
                                currentPageType = CommentPageType.EDIT
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit),
                                contentDescription = "Edit",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                onDeleteComment(commentModel.id)
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .padding(start = 10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    }
                    CommentPageType.ADD, CommentPageType.EDIT -> {
                        // No action buttons for add/edit mode
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(start = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_white),
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            // Snippet
            Text(
                text = commentModel.snippet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                fontSize = 16.sp,
                fontFamily = JakartaSansLight,
                color = Color.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            // Comment input
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = {
                    Text(
                        text = "Enter comment here",
                        fontFamily = JakartaSansRegular,
                        fontSize = 15.sp,
                        color = Color.Red
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = if (currentPageType != CommentPageType.VIEW) 20.dp else 40.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = JakartaSansRegular,
                    fontSize = 15.sp,
                    color = Color.Black
                ),
                enabled = currentPageType != CommentPageType.VIEW,
                minLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Black,
                    disabledBorderColor = Color.Black,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White
                ),
                shape = RoundedCornerShape(5.dp)
            )

            // Save button (only for ADD and EDIT modes)
            if (currentPageType != CommentPageType.VIEW) {
                Button(
                    onClick = {
                        val text = commentText.trim()
                        if (text.isEmpty()) {
                            // Show warning - implement Alerts.warningSnackBar equivalent
                            return@Button
                        }

                        val updatedComment = CommentModel(
                            id = commentModel.id,
                            snippet = commentModel.snippet,
                            text = text,
                            page = commentModel.page,
//                            updatedAt = 0L,
                            coordinates = commentModel.coordinates
                        )

                        when (currentPageType) {
                            CommentPageType.EDIT -> {
                                onEditCommentSave(updatedComment)
                            }
                            CommentPageType.ADD -> {
                                onAddCommentSave(updatedComment)
                            }
                            CommentPageType.VIEW -> {
                                // Should not reach here
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 10.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = when (currentPageType) {
                            CommentPageType.ADD -> "Add"
                            CommentPageType.EDIT -> "EDIT"
                            CommentPageType.VIEW -> "" // Should not reach here
                        },
                        fontSize = 20.sp,
                        fontFamily = JakartaSansSemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// Convenience functions for different use cases
@Composable
fun CommentViewBottomSheet(
    commentModel: CommentModel,
    onDeleteComment: (Long) -> Unit,
    onEditCommentSave: (CommentModel) -> Unit,
    onDismiss: () -> Unit
) {
    CommentViewBottomSheet(
        commentModel = commentModel,
        pageType = CommentPageType.VIEW,
        onDeleteComment = onDeleteComment,
        onEditCommentSave = onEditCommentSave,
        onAddCommentSave = { }, // Not used in view mode
        onDismiss = onDismiss
    )
}

@Composable
fun CommentEditBottomSheet(
    commentModel: CommentModel,
    onEditCommentSave: (CommentModel) -> Unit,
    onDismiss: () -> Unit
) {
    CommentViewBottomSheet(
        commentModel = commentModel,
        pageType = CommentPageType.EDIT,
        onDeleteComment = { }, // Not used in edit mode
        onEditCommentSave = onEditCommentSave,
        onAddCommentSave = { }, // Not used in edit mode
        onDismiss = onDismiss
    )
}

@Composable
fun CommentAddBottomSheet(
    commentModel: CommentModel,
    onAddCommentSave: (CommentModel) -> Unit,
    onDismiss: () -> Unit
) {
    CommentViewBottomSheet(
        commentModel = commentModel,
        pageType = CommentPageType.ADD,
        onDeleteComment = { }, // Not used in add mode
        onEditCommentSave = { }, // Not used in add mode
        onAddCommentSave = onAddCommentSave,
        onDismiss = onDismiss
    )
}