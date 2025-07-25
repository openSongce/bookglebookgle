package com.example.pdfnotemate.ui.activity.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf_room.response.PdfNoteListModel
import com.ssafy.bookglebookgle.pdf_room.response.PdfNotesResponse
import com.ssafy.bookglebookgle.pdf_room.state.ResponseState
import com.ssafy.bookglebookgle.pdf_room.ui.MoreOptionModel
import com.ssafy.bookglebookgle.pdf_room.ui.OptionPickBottomSheet
import com.ssafy.bookglebookgle.pdf_room.ui.screen.AddPdfPageType
import com.ssafy.bookglebookgle.pdf_room.utils.TagColors
import com.ssafy.bookglebookgle.pdf_room.viewmodel.HomeViewModel

// Font families
private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))
private val JakartaSansMedium = FontFamily(Font(R.font.jakarta_sans_medium_500, FontWeight.Medium))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))

@Composable
fun HomeScreen(
    onBackPressed: () -> Unit,
    onNavigateToAddPdf: (AddPdfPageType) -> Unit,
    onNavigateToPdfReader: (PdfNoteListModel) -> Unit,
    onPdfAdded: () -> Unit = {},
    onPdfDeleted: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // States
    var searchText by remember { mutableStateOf("") }
    var showAddOptions by remember { mutableStateOf(false) }
    var pdfList by remember { mutableStateOf<List<PdfNoteListModel>>(emptyList()) }
    var filteredPdfList by remember { mutableStateOf<List<PdfNoteListModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Handle PDF added/deleted callbacks
    LaunchedEffect(onPdfAdded) {
        // This will be called when returning from AddPdfScreen
    }

    LaunchedEffect(onPdfDeleted) {
        // Refresh PDF list when a PDF is deleted
        viewModel.getAllPdfs()
    }

    // Observe ViewModel state
    val pdfListState by viewModel.pdfListResponse.state.observeAsState()

    // Handle PDF list state changes
    LaunchedEffect(pdfListState) {
        pdfListState?.let { state ->
            when (state) {
                is ResponseState.Failed -> {
                    isLoading = false
                    errorMessage = state.error
                }
                ResponseState.Loading -> {
                    isLoading = true
                    errorMessage = null
                }
                is ResponseState.Success<*> -> {
                    isLoading = false
                    val response = state.response as? PdfNotesResponse
                    if (response != null) {
                        pdfList = response.notes
                        filteredPdfList = response.notes
                        errorMessage = if (response.notes.isEmpty()) {
                           "You don\\'t have any pdf notes yet"
                        } else null
                    }
                }
                is ResponseState.ValidationError -> {
                    isLoading = false
                }
            }
        }
    }

    // Filter PDF list when search text changes
    LaunchedEffect(searchText) {
        filteredPdfList = if (searchText.isEmpty()) {
            pdfList
        } else {
            pdfList.filter { pdf ->
                pdf.title.uppercase().contains(searchText.uppercase()) ||
                        (pdf.tag?.title?.uppercase()?.contains(searchText.uppercase()) ?: false)
            }
        }

        errorMessage = if (filteredPdfList.isEmpty() && searchText.isNotEmpty()) {
            "You don't have note with that details"
        } else if (filteredPdfList.isEmpty() && pdfList.isNotEmpty()) {
            "You don't have note with that details"
        } else if (pdfList.isEmpty()) {
            "You don\\'t have any pdf notes yet"
        } else {
            null
        }
    }

    // Load PDFs on first composition
    LaunchedEffect(Unit) {
        viewModel.getAllPdfs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red)
    ) {
        // Top Bar
        TopAppBar(
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            onBackPressed = onBackPressed,
            onAddPdfClicked = { showAddOptions = true }
        )

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 16.sp,
                        fontFamily = JakartaSansSemiBold,
                        color = Color.Black
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPdfList) { pdf ->
                            PdfListItem(
                                pdf = pdf,
                                onItemClick = { selectedPdf ->
                                    onNavigateToPdfReader(selectedPdf)
                                },
                                onItemLongClick = { selectedPdf ->
                                    // Handle long click if needed
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Options Bottom Sheet
    if (showAddOptions) {
        OptionPickBottomSheet(
            title = "Add New PDF",
            options = listOf(
                MoreOptionModel(1, "Pick From Gallery"),
                MoreOptionModel(2, "Download PDF")
            ),
            onOptionSelected = { option ->
                val pageType = if (option.id == 1) {
                    AddPdfPageType.PickFromGallery
                } else {
                    AddPdfPageType.DownloadPdf
                }
                onNavigateToAddPdf(pageType)
            },
            onDismiss = { showAddOptions = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onBackPressed: () -> Unit,
    onAddPdfClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Red)
    ) {
        // Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Notes",
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontFamily = JakartaSansSemiBold,
                color = Color.White
            )
        }

        // Search Bar and Add Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Bar
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    placeholder = {
                        Text(
                            text = "Search by Name or Tag",
                            fontSize = 14.sp,
                            fontFamily = JakartaSansMedium,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontFamily = JakartaSansMedium,
                        color = Color.Black
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Icon(
                    painter = painterResource(id = R.drawable.ic_search_white),
                    contentDescription = "Search",
                    modifier = Modifier
                        .size(50.dp)
                        .padding(10.dp),
                    tint = Color.Black
                )
            }

            // Add Button
            IconButton(
                onClick = onAddPdfClicked,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Add PDF",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
private fun PdfListItem(
    pdf: PdfNoteListModel,
    onItemClick: (PdfNoteListModel) -> Unit,
    onItemLongClick: (PdfNoteListModel) -> Unit
) {
    val tagColors = remember { TagColors() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(pdf) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = pdf.title,
                fontSize = 18.sp,
                fontFamily = JakartaSansSemiBold,
                color = Color.Black
            )

            // Tag (only show if not null or empty)
            if (!pdf.tag?.title.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(tagColors.getColor(pdf.tag?.id ?: -1L)))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = pdf.tag?.title ?: "",
                        fontSize = 12.sp,
                        fontFamily = JakartaSansRegular,
                        color = Color.White
                    )
                }
            }

            // About (only show if not null or empty)
            if (!pdf.about.isNullOrEmpty()) {
                Text(
                    text = pdf.about,
                    fontSize = 14.sp,
                    fontFamily = JakartaSansRegular,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2
                )
            }
        }
    }
}