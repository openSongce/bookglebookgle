package com.ssafy.bookglebookgle.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.flowlayout.FlowRow
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf.response.RemoveTagResponse
import com.ssafy.bookglebookgle.pdf.response.TagModel
import com.ssafy.bookglebookgle.pdf.state.ResponseState
import com.ssafy.bookglebookgle.pdf.utils.TagColors
import com.ssafy.bookglebookgle.pdf.viewmodel.TagViewModel
import com.ssafy.bookglebookgle.ui.theme.MainColor

// Font families (이미 정의되어 있다면 생략 가능)
private val JakartaSansBold = FontFamily(Font(R.font.jakarta_sans_bold_700, FontWeight.Bold))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))
private val JakartaSansMedium = FontFamily(Font(R.font.jakarta_sans_medium_500, FontWeight.Medium))
private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBottomSheet(
    onTagSelected: (TagModel) -> Unit,
    onTagRemoved: (Long) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TagViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val tagColors = remember { TagColors() }

    var tagName by remember { mutableStateOf("") }

    // ViewModel states
    val tagListState by viewModel.tagListResponse.state.observeAsState()
    val addTagState by viewModel.addTagResponse.state.observeAsState()
    val removeTagState by viewModel.removeTagResponse.state.observeAsState()

    // Tags list
    var tags by remember { mutableStateOf<List<TagModel>>(emptyList()) }

    // Load tags on first composition
    LaunchedEffect(Unit) {
        viewModel.loadAllTags()
    }

    // Handle states
    LaunchedEffect(tagListState) {
        when (tagListState) {
            is ResponseState.Failed -> {
                // Alerts.failureSnackBar equivalent - you might need to implement this
                // For now, you can use a simple Toast or implement custom snackbar
            }
            ResponseState.Loading -> {
                // Handle loading state if needed
            }
            is ResponseState.Success<*> -> {
                val response = ((tagListState as ResponseState.Success<*>).response as? List<*>)?.mapNotNull { it as? TagModel }
                if (response != null) {
                    tags = response
                }
            }
            is ResponseState.ValidationError -> {
                // Handle validation error
            }

            null -> {}
        }
    }

    LaunchedEffect(addTagState) {
        when (addTagState) {
            is ResponseState.Failed -> {
                // Handle failure
            }
            ResponseState.Loading -> {
                // Handle loading
            }
            is ResponseState.Success<*> -> {
                val response = (addTagState as ResponseState.Success<*>).response as? TagModel
                if (response != null && !tags.any { it.id == response.id }) {
                    tags = tags + response
                    tagName = ""
                }
            }
            is ResponseState.ValidationError -> {
                // Handle validation error
            }

            null -> {}
        }
    }

    LaunchedEffect(removeTagState) {
        when (removeTagState) {
            is ResponseState.Failed -> {
                // Handle failure
            }
            ResponseState.Loading -> {
                // Handle loading
            }
            is ResponseState.Success<*> -> {
                val response = (removeTagState as ResponseState.Success<*>).response as? RemoveTagResponse
                if (response != null) {
                    tags = tags.filter { it.id != response.tagId }
                    onTagRemoved(response.tagId)
                }
            }
            is ResponseState.ValidationError -> {
                // Handle validation error
            }

            null -> {}
        }
    }

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
                    .background(MainColor)
                    .padding(horizontal = 15.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "태그",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontFamily = JakartaSansBold,
                    color = Color.White
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_white),
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            // Tags FlexRow
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 20.dp),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    tags.forEach { tag ->
                        TagItem(
                            tag = tag,
                            backgroundColor = Color(tagColors.getColor(tag.id)),
                            onTagClick = {
                                onTagSelected(tag)
                                onDismiss()
                            },
                            onRemoveClick = {
                                viewModel.removeTag(tag.id)
                            }
                        )
                    }
                }
            }

            // Add tag section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 30.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { if (it.length <= 25) tagName = it },
                    placeholder = {
                        Text(
                            text = "태그를 추가하세요.",
                            fontFamily = JakartaSansRegular,
                            fontSize = 15.sp,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = JakartaSansRegular,
                        fontSize = 15.sp,
                        color = Color.Black
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MainColor,
                        unfocusedBorderColor = Color.Black,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    shape = RoundedCornerShape(5.dp)
                )

                Button(
                    onClick = {
                        val title = tagName.trim()
                        if (title.isEmpty()) {
                            // Show warning - implement Alerts.warningSnackBar equivalent
                            return@Button
                        }
                        viewModel.addTag(title)
                    },
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MainColor
                    ),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = "Add",
                        fontSize = 16.sp,
                        fontFamily = JakartaSansSemiBold,
                        color = Color.White
                    )
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TagItem(
    tag: TagModel,
    backgroundColor: Color,
    onTagClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(5.dp))
            .background(backgroundColor)
            .clickable { onTagClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag.title,
            fontSize = 14.sp,
            fontFamily = JakartaSansMedium,
            color = Color.White,
            modifier = Modifier.padding(end = 8.dp)
        )

        IconButton(
            onClick = onRemoveClick,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_white),
                contentDescription = "Remove tag",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}