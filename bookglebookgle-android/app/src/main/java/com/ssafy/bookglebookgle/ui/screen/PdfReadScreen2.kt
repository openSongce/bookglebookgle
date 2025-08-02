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
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.hilt.navigation.compose.hiltViewModel
//import androidx.navigation.NavHostController
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
//import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
//import com.ssafy.bookglebookgle.viewmodel.PdfViewModel
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import java.io.File
//import java.io.InputStream
//
//@Composable
//fun PdfReadScreen2(
//    groupId: Long? = null,
//    pdfDetails: PdfNoteListModel? = null,
//    viewModel: PdfViewModel = hiltViewModel(),
//    navController: NavHostController
//) {
//    val context = LocalContext.current
//
//    // PDF 상태를 안정화
//    var pdfReady by remember { mutableStateOf(false) }
//    var pdfFile by remember { mutableStateOf<File?>(null) }
//    var pdfTitle by remember { mutableStateOf("") }
//    var pdfLoadError by remember { mutableStateOf<String?>(null) }
//    var isLoading by remember { mutableStateOf(true) }
//
//    // PDF View states
//    var currentPage by remember { mutableStateOf(1) }
//    var totalPages by remember { mutableStateOf(1) }
//    var showPageInfo by remember { mutableStateOf(false) }
//    var pdfView by remember { mutableStateOf<PDFView?>(null) }
//    val pdfRenderScope = remember { CoroutineScope(Dispatchers.IO) }
//
//    // PDF 다운로드 및 준비
//    LaunchedEffect(groupId, pdfDetails) {
//        Log.d("PdfReadScreen", "=== PDF 준비 시작 ===")
//
//        when {
//            groupId != null -> {
//                Log.d("PdfReadScreen", "그룹 PDF 다운로드: $groupId")
//                pdfTitle = "Group PDF"
//
//                try {
//                    viewModel.loadGroupPdf(
//                        groupId = groupId,
//                        onSuccess = { inputStream ->
//                            Log.d("PdfReadScreen", "서버 다운로드 성공")
//
//                            try {
//                                val bytes = inputStream.readBytes()
//                                Log.d("PdfReadScreen", "PDF 크기: ${bytes.size} bytes")
//
//                                if (bytes.size >= 4 && String(bytes.sliceArray(0..3)) == "%PDF") {
//                                    // 임시 파일로 저장
//                                    val tempFile = File.createTempFile("group_pdf", ".pdf", context.cacheDir)
//                                    tempFile.writeBytes(bytes)
//
//                                    Log.d("PdfReadScreen", "임시 파일 저장: ${tempFile.absolutePath}")
//                                    pdfFile = tempFile
//                                    pdfReady = true
//                                    isLoading = false
//                                } else {
//                                    pdfLoadError = "유효하지 않은 PDF 파일"
//                                    isLoading = false
//                                }
//                            } catch (e: Exception) {
//                                Log.e("PdfReadScreen", "PDF 처리 실패", e)
//                                pdfLoadError = "PDF 처리 실패: ${e.message}"
//                                isLoading = false
//                            }
//                        },
//                        onError = { error ->
//                            Log.e("PdfReadScreen", "다운로드 실패: $error")
//                            pdfLoadError = error
//                            isLoading = false
//                        }
//                    )
//                } catch (e: Exception) {
//                    Log.e("PdfReadScreen", "다운로드 호출 실패", e)
//                    pdfLoadError = "다운로드 실패: ${e.message}"
//                    isLoading = false
//                }
//            }
//
//            pdfDetails != null -> {
//                Log.d("PdfReadScreen", "로컬 PDF: ${pdfDetails.filePath}")
//                pdfTitle = pdfDetails.title
//
//                val file = File(pdfDetails.filePath)
//                if (file.exists()) {
//                    pdfFile = file
//                    pdfReady = true
//                    isLoading = false
//                } else {
//                    pdfLoadError = "파일을 찾을 수 없습니다"
//                    isLoading = false
//                }
//            }
//
//            else -> {
//                pdfLoadError = "PDF 정보가 없습니다"
//                isLoading = false
//            }
//        }
//
//        Log.d("PdfReadScreen", "=== PDF 준비 완료 ===")
//    }
//
//    Column(modifier = Modifier.fillMaxSize()) {
//        // Top Bar
//        CustomTopAppBar(title = pdfTitle, navController = navController)
//
//        // Content
//        Box(modifier = Modifier.fillMaxSize()) {
//            when {
//                isLoading -> {
//                    // 로딩 중
//                    Column(
//                        modifier = Modifier.fillMaxSize(),
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        verticalArrangement = Arrangement.Center
//                    ) {
//                        CircularProgressIndicator()
//                        Spacer(modifier = Modifier.height(16.dp))
//                        Text("PDF 다운로드 중...")
//                    }
//                }
//
//                pdfLoadError != null -> {
//                    // 에러 상태
//                    Column(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(16.dp),
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        verticalArrangement = Arrangement.Center
//                    ) {
//                        Text("오류 발생", fontSize = 20.sp, color = Color.Red)
//                        Spacer(modifier = Modifier.height(8.dp))
//                        Text(pdfLoadError!!, fontSize = 16.sp, textAlign = TextAlign.Center)
//                        Spacer(modifier = Modifier.height(16.dp))
//                        Button(onClick = { navController.popBackStack() }) {
//                            Text("뒤로가기")
//                        }
//                    }
//                }
//
//                pdfReady && pdfFile != null -> {
//                    // PDF 표시
//                    Log.d("PdfReadScreen", "PDF AndroidView 생성 - 파일: ${pdfFile!!.absolutePath}")
//
//                    AndroidView(
//                        factory = { context ->
//                            Log.d("PdfReadScreen", "AndroidView factory 실행")
//
//                            PDFView(context, null).apply {
//                                pdfView = this
//                                attachCoroutineScope(pdfRenderScope)
//
//                                setListener(object : PDFView.Listener {
//                                    override fun onPreparationStarted() {
//                                        Log.d("PdfReadScreen", "PDF 렌더링 시작")
//                                    }
//
//                                    override fun onPreparationSuccess() {
//                                        Log.d("PdfReadScreen", "PDF 렌더링 성공")
//                                        totalPages = getTotalPage()
//                                        Log.d("PdfReadScreen", "총 페이지: $totalPages")
//                                    }
//
//                                    override fun onPreparationFailed(error: String, e: Exception?) {
//                                        Log.e("PdfReadScreen", "PDF 렌더링 실패: $error", e)
//                                    }
//
//                                    override fun onPageChanged(pageIndex: Int, paginationPageIndex: Int) {
//                                        currentPage = pageIndex + 1
//                                        showPageInfo = true
//                                        Log.d("PdfReadScreen", "페이지 변경: $currentPage")
//                                    }
//
//                                    override fun onTextSelected(selection: TextSelectionData, rawPoint: PointF) {}
//                                    override fun hideTextSelectionOptionWindow() {}
//                                    override fun onTextSelectionCleared() {}
//                                    override fun onNotesStampsClicked(comments: List<CommentModel>, pointOfNote: PointF) {}
//                                    override fun loadTopPdfChunk(mergeId: Int, pageIndexToLoad: Int) {}
//                                    override fun loadBottomPdfChunk(mergedId: Int, pageIndexToLoad: Int) {}
//                                    override fun onScrolling() {}
//                                    override fun onTap() {}
//                                    override fun onMergeStart(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                                    override fun onMergeEnd(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                                    override fun onMergeFailed(mergeId: Int, mergeType: PdfFile.MergeType, message: String, exception: java.lang.Exception?) {}
//                                })
//
//                                // PDF 로드
//                                Log.d("PdfReadScreen", "PDF 파일 로드 시작: ${pdfFile!!.absolutePath}")
//                                fromFile(pdfFile!!).defaultPage(0).load()
//                                Log.d("PdfReadScreen", "PDF 파일 로드 호출 완료")
//                            }
//                        },
//                        modifier = Modifier.fillMaxSize()
//                    )
//                }
//            }
//
//            // 페이지 정보 표시
//            if (showPageInfo && totalPages > 0) {
//                Card(
//                    modifier = Modifier
//                        .align(Alignment.TopEnd)
//                        .padding(10.dp),
//                    colors = CardDefaults.cardColors(containerColor = Color.Gray)
//                ) {
//                    Text(
//                        text = "$currentPage/$totalPages",
//                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
//                        fontSize = 17.sp,
//                        color = Color.White
//                    )
//                }
//
//                LaunchedEffect(currentPage) {
//                    kotlinx.coroutines.delay(3000)
//                    showPageInfo = false
//                }
//            }
//        }
//    }
//}