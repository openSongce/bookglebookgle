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
import androidx.compose.ui.draw.clip
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
import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf.state.ResponseState
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf.viewmodel.HighlightListViewModel

// Font families
private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))
private val JakartaSansLight = FontFamily(Font(R.font.jakarta_sans_light_300, FontWeight.Light))

enum class HighlightScreenMode {
    IDLE,
    SELECTION_MODE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightListScreen(
    initialHighlights: List<HighlightModel>,
    onBackPressed: (deletedHighlightIds: List<Long>) -> Unit,
    onHighlightClicked: (HighlightModel, deletedHighlightIds: List<Long>) -> Unit,
    viewModel: HighlightListViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // States
    var highlights by remember { mutableStateOf(initialHighlights.toMutableList()) }
    var screenMode by remember { mutableStateOf(HighlightScreenMode.IDLE) }
    var deletedHighlightIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedHighlights by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Observe ViewModel state
    val deleteHighlightState by viewModel.deleteHighlightResponse.state.observeAsState()

    // Handle delete highlight state changes
    LaunchedEffect(deleteHighlightState) {
        deleteHighlightState?.let { state ->
            when (state) {
                is ResponseState.Success<*> -> {
                    val response = state.response as? DeleteAnnotationResponse
                    response?.let {
                        deletedHighlightIds = deletedHighlightIds + response.deletedIds
                        highlights.removeAll { highlight -> response.deletedIds.contains(highlight.id) }
                        selectedHighlights = emptySet()
                        screenMode = HighlightScreenMode.IDLE
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
        if (screenMode == HighlightScreenMode.SELECTION_MODE) {
            selectedHighlights = emptySet()
            screenMode = HighlightScreenMode.IDLE
        } else {
            onBackPressed(deletedHighlightIds)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Highlights",
                    fontSize = 20.sp,
                    fontFamily = JakartaSansSemiBold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (screenMode == HighlightScreenMode.SELECTION_MODE) {
                            selectedHighlights = emptySet()
                            screenMode = HighlightScreenMode.IDLE
                        } else {
                            onBackPressed(deletedHighlightIds)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (screenMode == HighlightScreenMode.SELECTION_MODE) {
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
                if (screenMode == HighlightScreenMode.SELECTION_MODE) {
                    IconButton(
                        onClick = {
                            val idsToDelete = selectedHighlights.map { index -> highlights[index].id }
                            viewModel.deleteHighlights(idsToDelete)
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
            if (highlights.isEmpty()) {
                Text(
                    text = "No Highlight found",
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
                    itemsIndexed(highlights) { index, highlight ->
                        HighlightListItem(
                            highlight = highlight,
                            isSelected = selectedHighlights.contains(index),
                            screenMode = screenMode,
                            onClick = {
                                when (screenMode) {
                                    HighlightScreenMode.IDLE -> {
                                        onHighlightClicked(highlight, deletedHighlightIds)
                                    }
                                    HighlightScreenMode.SELECTION_MODE -> {
                                        selectedHighlights = if (selectedHighlights.contains(index)) {
                                            val newSelection = selectedHighlights - index
                                            if (newSelection.isEmpty()) {
                                                screenMode = HighlightScreenMode.IDLE
                                            }
                                            newSelection
                                        } else {
                                            selectedHighlights + index
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                selectedHighlights = selectedHighlights + index
                                screenMode = HighlightScreenMode.SELECTION_MODE
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightListItem(
    highlight: HighlightModel,
    isSelected: Boolean,
    screenMode: HighlightScreenMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    // Parse highlight color and add alpha
    val highlightColor = remember(highlight.color) {
        try {
            val baseColor = android.graphics.Color.parseColor(highlight.color)
            val alpha = 0x60
            android.graphics.Color.argb(
                alpha,
                android.graphics.Color.red(baseColor),
                android.graphics.Color.green(baseColor),
                android.graphics.Color.blue(baseColor)
            )
        } catch (e: Exception) {
            android.graphics.Color.argb(0x60, 255, 255, 0) // Default yellow with alpha
        }
    }

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
                Spacer(modifier = Modifier.height(10.dp))

                // Highlighted Snippet
                Text(
                    text = highlight.snippet,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(highlightColor))
                        .padding(4.dp),
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
                        text = com.ssafy.bookglebookgle.pdf.tools.DateTimeFormatter.format(
                            highlight.updatedAt,
                            com.ssafy.bookglebookgle.pdf.tools.DateTimeFormatter.DATE_AND_TIME_THREE
                        ),
                        fontSize = 15.sp,
                        fontFamily = JakartaSansRegular,
                        color = Color.Gray
                    )

                    Text(
                        text = "Page" + highlight.page,
                        fontSize = 15.sp,
                        fontFamily = JakartaSansRegular,
                        color = Color.Gray
                    )
                }
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