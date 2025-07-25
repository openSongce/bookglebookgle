package com.ssafy.bookglebookgle.pdf_room.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf_room.response.PdfNoteListModel
import com.ssafy.bookglebookgle.pdf_room.response.TagModel
import com.ssafy.bookglebookgle.pdf_room.state.ResponseState
import com.ssafy.bookglebookgle.pdf_room.tools.AppFileManager
import com.ssafy.bookglebookgle.pdf_room.tools.FileDownloadTool
import com.ssafy.bookglebookgle.pdf_room.ui.TagBottomSheet
import com.ssafy.bookglebookgle.pdf_room.viewmodel.AddPdfViewModel
import java.io.File
import java.io.FileOutputStream

// Font families
private val JakartaSansBold = FontFamily(Font(R.font.jakarta_sans_bold_700, FontWeight.Bold))
private val JakartaSansRegular = FontFamily(Font(R.font.jakarta_sans_regular_400, FontWeight.Normal))
private val JakartaSansMedium = FontFamily(Font(R.font.jakarta_sans_medium_500, FontWeight.Medium))
private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))

enum class AddPdfPageType {
    PickFromGallery,
    DownloadPdf
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPdfScreen(
    pageType: AddPdfPageType,
    onBackPressed: () -> Unit,
    onPdfAdded: () -> Unit,
    viewModel: AddPdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // States
    var pdfUrl by remember { mutableStateOf("") }
    var pdfTitle by remember { mutableStateOf("") }
    var aboutText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<TagModel?>(null) }
    var showTagBottomSheet by remember { mutableStateOf(false) }
    var isPdfImported by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    // Activity result launcher for PDF picking
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver?.openInputStream(it)?.use { input ->
                    val saveFile = AppFileManager.getNewPdfFile(context)
                    FileOutputStream(saveFile).use { output ->
                        input.copyTo(output)
                    }
                    viewModel.pdfFile = saveFile
                    isPdfImported = true
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Observe ViewModel state
    val pdfAddState by viewModel.pdfAddResponse.state.observeAsState()

    // Handle PDF add state changes
    LaunchedEffect(pdfAddState) {
        pdfAddState?.let { state ->
            when (state) {
                is ResponseState.Success<*> -> {
                    val response = state.response as? PdfNoteListModel
                    if (response != null) {
                        onPdfAdded()
                    }
                }
                is ResponseState.ValidationError -> {
                    // Show validation error
                }
                is ResponseState.Failed -> {
                    // Show failure error
                }
                ResponseState.Loading -> {
                    // Handle loading
                }
            }
        }
    }

    // Update viewModel selectedTag when local state changes
    LaunchedEffect(selectedTag) {
        viewModel.selectedTag = selectedTag
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Blue)
    ) {
        // Top Bar
        TopAppBar(
            title = "Add PDF",
            onBackPressed = onBackPressed
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // PDF Import Section
            if (isPdfImported) {
                PdfImportSuccessSection(
                    onRemovePdf = {
                        viewModel.pdfFile?.delete()
                        viewModel.pdfFile = null
                        isPdfImported = false
                    }
                )
            } else {
                when (pageType) {
                    AddPdfPageType.DownloadPdf -> {
                        DownloadSection(
                            pdfUrl = pdfUrl,
                            onPdfUrlChange = { pdfUrl = it },
                            onDownloadClick = {
                                if (pdfUrl.isNotEmpty()) {
                                    val saveFolder = AppFileManager.getPdfNoteFolder(context)
                                    if (saveFolder != null) {
                                        viewModel.downloadPdf(
                                            pdfUrl,
                                            saveFolder,
                                            object : FileDownloadTool.DownloadCallback {
                                                override fun onDownloadStart() {
                                                    isDownloading = true
                                                }

                                                override fun onDownloadFailed(error: String?) {
                                                    isDownloading = false
                                                    // Show error
                                                }

                                                override fun onDownloading(progress: Double) {
                                                    downloadProgress = progress.toInt()
                                                }

                                                override fun onDownloadCompleted(file: File) {
                                                    isDownloading = false
                                                    viewModel.pdfFile = file
                                                    isPdfImported = true
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            isDownloading = isDownloading,
                            downloadProgress = downloadProgress
                        )
                    }
                    AddPdfPageType.PickFromGallery -> {
                        PickFromGallerySection(
                            onPickPdfClick = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title Input
            Text(
                text = "Title",
                fontSize = 18.sp,
                fontFamily = JakartaSansBold,
                color = Color.White
            )

            OutlinedTextField(
                value = pdfTitle,
                onValueChange = { pdfTitle = it },
                placeholder = {
                    Text(
                        text = "Enter Pdf title here",
                        fontSize = 15.sp,
                        fontFamily = JakartaSansRegular,
                        color = Color.Gray
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    fontFamily = JakartaSansRegular,
                    color = Color.Black
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(5.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tag Section
            Text(
                text = "Tag",
                fontSize = 18.sp,
                fontFamily = JakartaSansBold,
                color = Color.White
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
                    .clickable { showTagBottomSheet = true }
                    .padding(horizontal = 10.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedTag?.title ?: "Add Tag",
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontFamily = JakartaSansRegular,
                    color = if (selectedTag != null) Color.Black else Color.Gray
                )

                if (selectedTag != null) {
                    IconButton(
                        onClick = { selectedTag = null },
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color = Color.Red)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close_white),
                            contentDescription = "Remove tag",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About Input
            Text(
                text = "About (Optional)",
                fontSize = 18.sp,
                fontFamily = JakartaSansBold,
                color = Color.White
            )

            OutlinedTextField(
                value = aboutText,
                onValueChange = { aboutText = it },
                placeholder = {
                    Text(
                        text = "Enter about the pdf",
                        fontSize = 15.sp,
                        fontFamily = JakartaSansRegular,
                        color = Color.Gray
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    fontFamily = JakartaSansRegular,
                    color = Color.Black
                ),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(5.dp)
            )

            Spacer(modifier = Modifier.height(50.dp))

            // Add PDF Button
            Button(
                onClick = {
                    viewModel.addPdf(pdfTitle.trim(), aboutText.trim())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                shape = RoundedCornerShape(5.dp)
            ) {
                Text(
                    text = "Add PDF",
                    fontSize = 18.sp,
                    fontFamily = JakartaSansSemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(50.dp))
        }
    }

    // Tag Bottom Sheet
    if (showTagBottomSheet) {
        TagBottomSheet(
            onTagSelected = { tag ->
                selectedTag = tag
            },
            onTagRemoved = { tagId ->
                if (selectedTag?.id == tagId) {
                    selectedTag = null
                }
            },
            onDismiss = { showTagBottomSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    title: String,
    onBackPressed: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Red)
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
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontFamily = JakartaSansSemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun PdfImportSuccessSection(
    onRemovePdf: () -> Unit
) {
    Column {
        Text(
            text = "PDF",
            fontSize = 20.sp,
            fontFamily = JakartaSansBold,
            color = Color.White
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pdf),
                contentDescription = "PDF",
                modifier = Modifier.size(70.dp),
                tint = Color.White
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imported",
                        fontSize = 18.sp,
                        fontFamily = JakartaSansSemiBold,
                        color = Color.White
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_thumbs_up),
                        contentDescription = "Success",
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(20.dp),
                        tint = Color.White
                    )
                }

                Button(
                    onClick = onRemovePdf,
                    modifier = Modifier.padding(top = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = "Remove",
                        fontSize = 17.sp,
                        fontFamily = JakartaSansMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSection(
    pdfUrl: String,
    onPdfUrlChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Int
) {
    Column {
        Text(
            text = "PDF Url",
            fontSize = 18.sp,
            fontFamily = JakartaSansBold,
            color = Color.White
        )

        OutlinedTextField(
            value = pdfUrl,
            onValueChange = onPdfUrlChange,
            placeholder = {
                Text(
                    text = "Enter Pdf title here",
                    fontSize = 15.sp,
                    fontFamily = JakartaSansRegular,
                    color = Color.Gray
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                fontFamily = JakartaSansRegular,
                color = Color.Black
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            shape = RoundedCornerShape(5.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onDownloadClick,
                enabled = !isDownloading && pdfUrl.isNotEmpty(),
                modifier = Modifier
                    .wrapContentWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                ),
                shape = RoundedCornerShape(5.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Download",
                        fontSize = 16.sp,
                        fontFamily = JakartaSansSemiBold,
                        color = Color.White
                    )
                }
            }
        }

        if (isDownloading && downloadProgress > 0) {
            LinearProgressIndicator(
                progress = downloadProgress / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                color = Color.Red
            )
        }
    }
}

@Composable
private fun PickFromGallerySection(
    onPickPdfClick: () -> Unit
) {
    Column {
        Text(
            text = "PDF",
            fontSize = 18.sp,
            fontFamily = JakartaSansBold,
            color = Color.White
        )

        Button(
            onClick = onPickPdfClick,
            modifier = Modifier
                .padding(top = 10.dp)
                .wrapContentWidth()
                .height(40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red
            ),
            shape = RoundedCornerShape(5.dp)
        ) {
            Text(
                text = "Pick PDF",
                fontSize = 16.sp,
                fontFamily = JakartaSansSemiBold,
                color = Color.White
            )
        }
    }
}