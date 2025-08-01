package com.example.pdfnotemate.ui.activity.reader

import android.graphics.PointF
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf.response.PdfNoteListModel
import com.ssafy.bookglebookgle.pdf.state.ResponseState
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PdfFile
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.PdfAnnotationModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.TextSelectionData
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.selection.TextSelectionOptionsWindow
import com.ssafy.bookglebookgle.pdf.ui.CommentAddBottomSheet
import com.ssafy.bookglebookgle.pdf.ui.CommentEditBottomSheet
import com.ssafy.bookglebookgle.pdf.ui.CommentPageType
import com.ssafy.bookglebookgle.pdf.ui.CommentViewBottomSheet
import com.ssafy.bookglebookgle.pdf.ui.MoreOptionModel
import com.ssafy.bookglebookgle.pdf.ui.OptionPickBottomSheet
import com.ssafy.bookglebookgle.pdf.ui.screen.BookmarkListScreen
import com.ssafy.bookglebookgle.pdf.ui.screen.CommentsListScreen
import com.ssafy.bookglebookgle.pdf.ui.screen.HighlightListScreen
import com.ssafy.bookglebookgle.pdf.viewmodel.PdfReaderViewModel
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.viewmodel.PdfViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.InputStream

@Composable
fun PdfReadScreen(
    groupId: Long? = null,
    pdfDetails: PdfNoteListModel? = null,
    viewModel: PdfViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val context = LocalContext.current

    // PDF 상태를 안정화
    var pdfReady by remember { mutableStateOf(false) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfTitle by remember { mutableStateOf("") }
    var pdfLoadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // PDF View states
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var showPageInfo by remember { mutableStateOf(false) }
    var pdfView by remember { mutableStateOf<PDFView?>(null) }
    val pdfRenderScope = remember { CoroutineScope(Dispatchers.IO) }

    // PDF 다운로드 및 준비
    LaunchedEffect(groupId, pdfDetails) {
        Log.d("PdfReadScreen", "=== PDF 준비 시작 ===")

        when {
            groupId != null -> {
                Log.d("PdfReadScreen", "그룹 PDF 다운로드: $groupId")
                pdfTitle = "Group PDF"

                try {
                    viewModel.loadGroupPdf(
                        groupId = groupId,
                        onSuccess = { inputStream ->
                            Log.d("PdfReadScreen", "서버 다운로드 성공")

                            try {
                                val bytes = inputStream.readBytes()
                                Log.d("PdfReadScreen", "PDF 크기: ${bytes.size} bytes")

                                if (bytes.size >= 4 && String(bytes.sliceArray(0..3)) == "%PDF") {
                                    // 임시 파일로 저장
                                    val tempFile = File.createTempFile("group_pdf", ".pdf", context.cacheDir)
                                    tempFile.writeBytes(bytes)

                                    Log.d("PdfReadScreen", "임시 파일 저장: ${tempFile.absolutePath}")
                                    pdfFile = tempFile
                                    pdfReady = true
                                    isLoading = false
                                } else {
                                    pdfLoadError = "유효하지 않은 PDF 파일"
                                    isLoading = false
                                }
                            } catch (e: Exception) {
                                Log.e("PdfReadScreen", "PDF 처리 실패", e)
                                pdfLoadError = "PDF 처리 실패: ${e.message}"
                                isLoading = false
                            }
                        },
                        onError = { error ->
                            Log.e("PdfReadScreen", "다운로드 실패: $error")
                            pdfLoadError = error
                            isLoading = false
                        }
                    )
                } catch (e: Exception) {
                    Log.e("PdfReadScreen", "다운로드 호출 실패", e)
                    pdfLoadError = "다운로드 실패: ${e.message}"
                    isLoading = false
                }
            }

            pdfDetails != null -> {
                Log.d("PdfReadScreen", "로컬 PDF: ${pdfDetails.filePath}")
                pdfTitle = pdfDetails.title

                val file = File(pdfDetails.filePath)
                if (file.exists()) {
                    pdfFile = file
                    pdfReady = true
                    isLoading = false
                } else {
                    pdfLoadError = "파일을 찾을 수 없습니다"
                    isLoading = false
                }
            }

            else -> {
                pdfLoadError = "PDF 정보가 없습니다"
                isLoading = false
            }
        }

        Log.d("PdfReadScreen", "=== PDF 준비 완료 ===")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        CustomTopAppBar(title = pdfTitle, navController = navController)

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    // 로딩 중
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("PDF 다운로드 중...")
                    }
                }

                pdfLoadError != null -> {
                    // 에러 상태
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("오류 발생", fontSize = 20.sp, color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(pdfLoadError!!, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.popBackStack() }) {
                            Text("뒤로가기")
                        }
                    }
                }

                pdfReady && pdfFile != null -> {
                    // PDF 표시
                    Log.d("PdfReadScreen", "PDF AndroidView 생성 - 파일: ${pdfFile!!.absolutePath}")

                    AndroidView(
                        factory = { context ->
                            Log.d("PdfReadScreen", "AndroidView factory 실행")

                            PDFView(context, null).apply {
                                pdfView = this
                                attachCoroutineScope(pdfRenderScope)

                                setListener(object : PDFView.Listener {
                                    override fun onPreparationStarted() {
                                        Log.d("PdfReadScreen", "PDF 렌더링 시작")
                                    }

                                    override fun onPreparationSuccess() {
                                        Log.d("PdfReadScreen", "PDF 렌더링 성공")
                                        totalPages = getTotalPage()
                                        Log.d("PdfReadScreen", "총 페이지: $totalPages")
                                    }

                                    override fun onPreparationFailed(error: String, e: Exception?) {
                                        Log.e("PdfReadScreen", "PDF 렌더링 실패: $error", e)
                                    }

                                    override fun onPageChanged(pageIndex: Int, paginationPageIndex: Int) {
                                        currentPage = pageIndex + 1
                                        showPageInfo = true
                                        Log.d("PdfReadScreen", "페이지 변경: $currentPage")
                                    }

                                    override fun onTextSelected(selection: TextSelectionData, rawPoint: PointF) {}
                                    override fun hideTextSelectionOptionWindow() {}
                                    override fun onTextSelectionCleared() {}
                                    override fun onNotesStampsClicked(comments: List<CommentModel>, pointOfNote: PointF) {}
                                    override fun loadTopPdfChunk(mergeId: Int, pageIndexToLoad: Int) {}
                                    override fun loadBottomPdfChunk(mergedId: Int, pageIndexToLoad: Int) {}
                                    override fun onScrolling() {}
                                    override fun onTap() {}
                                    override fun onMergeStart(mergeId: Int, mergeType: PdfFile.MergeType) {}
                                    override fun onMergeEnd(mergeId: Int, mergeType: PdfFile.MergeType) {}
                                    override fun onMergeFailed(mergeId: Int, mergeType: PdfFile.MergeType, message: String, exception: java.lang.Exception?) {}
                                })

                                // PDF 로드
                                Log.d("PdfReadScreen", "PDF 파일 로드 시작: ${pdfFile!!.absolutePath}")
                                fromFile(pdfFile!!).defaultPage(0).load()
                                Log.d("PdfReadScreen", "PDF 파일 로드 호출 완료")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 페이지 정보 표시
            if (showPageInfo && totalPages > 0) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Gray)
                ) {
                    Text(
                        text = "$currentPage/$totalPages",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontSize = 17.sp,
                        color = Color.White
                    )
                }

                LaunchedEffect(currentPage) {
                    kotlinx.coroutines.delay(3000)
                    showPageInfo = false
                }
            }
        }
    }
}


//
//// Font families
//private val JakartaSansSemiBold = FontFamily(Font(R.font.jakarta_sans_semibold_600, FontWeight.SemiBold))
//
//@Composable
//fun PdfReadScreen(
//    groupId: Long? = null, // 그룹 PDF용 파라미터
//    pdfDetails: PdfNoteListModel? = null, // 기존 로컬 PDF용 파라미터
//    viewModel: PdfViewModel = hiltViewModel(),
//    navController: NavHostController
//) {
//    val context = LocalContext.current
//
//    // 디버깅 로그 추가
//    Log.d("PdfReadScreen", "화면 시작 - groupId: $groupId, pdfDetails: ${pdfDetails?.title}")
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
//    var pdfInputStream by remember { mutableStateOf<InputStream?>(null) }
//    var pdfTitle by remember { mutableStateOf("") }
//    var pdfLoadError by remember { mutableStateOf<String?>(null) }
//
//    // PDF View and related components
//    var pdfView by remember { mutableStateOf<PDFView?>(null) }
//    var textSelectionWindow by remember { mutableStateOf<TextSelectionOptionsWindow?>(null) }
//    val pdfRenderScope = remember { CoroutineScope(Dispatchers.IO) }
//
//    // 테스트용 하드코딩
//    LaunchedEffect(pdfDetails, groupId) {
//        Log.d("PdfReadScreen", "LaunchedEffect 시작")
//
//        if (groupId != null) {
//            pdfTitle = "Group PDF"
//
//            // 실제 서버에서 PDF 다운로드
//            Log.d("PdfReadScreen", "실제 서버에서 PDF 다운로드 시작 - groupId: $groupId")
//
//            try {
//                viewModel.loadGroupPdf(
//                    groupId = groupId,
//                    onSuccess = { inputStream ->
//                        Log.d("PdfReadScreen", "서버에서 PDF 다운로드 성공")
//
//                        try {
//                            // InputStream을 ByteArray로 읽어서 크기 확인
//                            val bytes = inputStream.readBytes()
//                            Log.d("PdfReadScreen", "다운로드된 PDF 크기: ${bytes.size} bytes")
//
//                            // PDF 헤더 확인 (PDF 파일은 "%PDF"로 시작)
//                            if (bytes.size >= 4) {
//                                val header = String(bytes.sliceArray(0..3))
//                                Log.d("PdfReadScreen", "PDF 헤더: '$header'")
//
//                                if (header == "%PDF") {
//                                    // 유효한 PDF 파일
//                                    pdfInputStream = bytes.inputStream()
//                                    Log.d("PdfReadScreen", "유효한 PDF 파일 확인 - 로딩 준비 완료")
//                                    isLoading = false // 다운로드 완료
//                                } else {
//                                    Log.e("PdfReadScreen", "유효하지 않은 PDF 파일 - 헤더: '$header'")
//
//                                    // 서버에서 에러 메시지를 HTML로 보낸 경우 확인
//                                    val responseText = String(bytes.take(200).toByteArray())
//                                    Log.e("PdfReadScreen", "서버 응답 내용 (처음 200자): $responseText")
//
//                                    pdfLoadError = "서버에서 유효하지 않은 PDF 데이터를 받았습니다"
//                                    isLoading = false
//                                }
//                            } else {
//                                Log.e("PdfReadScreen", "PDF 파일이 너무 작음: ${bytes.size} bytes")
//                                pdfLoadError = "다운로드된 파일이 너무 작습니다"
//                                isLoading = false
//                            }
//
//                        } catch (e: Exception) {
//                            Log.e("PdfReadScreen", "PDF 데이터 처리 중 오류", e)
//                            pdfLoadError = "PDF 데이터 처리 실패: ${e.message}"
//                            isLoading = false
//                        }
//                    },
//                    onError = { errorMessage ->
//                        Log.e("PdfReadScreen", "PDF 다운로드 실패: $errorMessage")
//                        pdfLoadError = errorMessage
//                        isLoading = false
//                    }
//                )
//            } catch (e: Exception) {
//                Log.e("PdfReadScreen", "loadGroupPdf 호출 실패", e)
//                pdfLoadError = "PDF 로딩 실패: ${e.message}"
//                isLoading = false
//            }
//
//        } else if (pdfDetails != null) {
//            pdfTitle = pdfDetails.title
//            Log.d("PdfReadScreen", "로컬 PDF: ${pdfDetails.filePath}")
//            isLoading = false // 로컬 파일은 바로 로딩 가능
//        } else {
//            Log.w("PdfReadScreen", "groupId와 pdfDetails 모두 null")
//            pdfLoadError = "PDF 정보가 없습니다"
//            isLoading = false
//        }
//    }
//    // PDF Content
//    Box(
//        modifier = Modifier.fillMaxSize()
//    ) {
//        // PDF가 준비된 경우에만 AndroidView 생성
//        if (pdfInputStream != null || (pdfDetails?.filePath != null)) {
//            Log.d("PdfReadScreen", "AndroidView 생성 - pdfInputStream: ${pdfInputStream != null}, filePath: ${pdfDetails?.filePath}")
//
//            AndroidView(
//                factory = { context ->
//                    Log.d("PdfReadScreen", "AndroidView factory 시작")
//
//                    PDFView(context, null).apply {
//                        pdfView = this
//                        attachCoroutineScope(pdfRenderScope)
//                        setListener(object : PDFView.Listener {
//                            override fun onPreparationStarted() {
//                                Log.d("PdfReadScreen", "PDF 준비 시작")
//                                // isLoading은 이미 false로 설정되어 있음 (다운로드 완료 후)
//                            }
//
//                            override fun onPreparationSuccess() {
//                                Log.d("PdfReadScreen", "PDF 준비 성공")
//                                totalPages = getTotalPage()
//                                Log.d("PdfReadScreen", "총 페이지: $totalPages")
//                            }
//
//                            override fun onPreparationFailed(error: String, e: Exception?) {
//                                Log.e("PdfReadScreen", "PDF 준비 실패: $error", e)
//                                Toast.makeText(context, "PDF 로딩 실패: $error", Toast.LENGTH_SHORT).show()
//                            }
//
//                            override fun onPageChanged(pageIndex: Int, paginationPageIndex: Int) {
//                                Log.d("PdfReadScreen", "페이지 변경: ${pageIndex + 1}")
//                                currentPage = pageIndex + 1
//                                showPageInfo = true
//                            }
//
//                            // 나머지 리스너들...
//                            override fun onTextSelected(selection: TextSelectionData, rawPoint: PointF) {}
//                            override fun hideTextSelectionOptionWindow() {}
//                            override fun onTextSelectionCleared() {}
//                            override fun onNotesStampsClicked(comments: List<CommentModel>, pointOfNote: PointF) {}
//                            override fun loadTopPdfChunk(mergeId: Int, pageIndexToLoad: Int) {}
//                            override fun loadBottomPdfChunk(mergedId: Int, pageIndexToLoad: Int) {}
//                            override fun onScrolling() {}
//                            override fun onTap() {}
//                            override fun onMergeStart(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                            override fun onMergeEnd(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                            override fun onMergeFailed(mergeId: Int, mergeType: PdfFile.MergeType, message: String, exception: java.lang.Exception?) {}
//                        })
//
//                        // PDF 로딩
//                        when {
//                            pdfInputStream != null -> {
//                                Log.d("PdfReadScreen", "그룹 PDF 스트림으로 로딩")
//                                try {
//                                    val tempFile = File.createTempFile("group_pdf", ".pdf", context.cacheDir)
//                                    Log.d("PdfReadScreen", "임시 파일 생성: ${tempFile.absolutePath}")
//
//                                    tempFile.outputStream().use { output ->
//                                        pdfInputStream!!.copyTo(output)
//                                    }
//
//                                    Log.d("PdfReadScreen", "임시 파일 크기: ${tempFile.length()} bytes")
//
//                                    if (tempFile.length() > 0) {
//                                        fromFile(tempFile).defaultPage(0).load()
//                                        Log.d("PdfReadScreen", "fromFile().load() 호출 완료")
//                                    } else {
//                                        Log.e("PdfReadScreen", "임시 파일이 비어있음")
//                                        Toast.makeText(context, "PDF 파일이 비어있습니다", Toast.LENGTH_SHORT).show()
//                                    }
//
//                                } catch (e: Exception) {
//                                    Log.e("PdfReadScreen", "그룹 PDF 로딩 실패", e)
//                                    Toast.makeText(context, "PDF 로딩 실패: ${e.message}", Toast.LENGTH_SHORT).show()
//                                }
//                            }
//                            pdfDetails?.filePath != null -> {
//                                Log.d("PdfReadScreen", "로컬 PDF 파일 로딩: ${pdfDetails.filePath}")
//                                val file = File(pdfDetails.filePath)
//                                if (file.exists()) {
//                                    Log.d("PdfReadScreen", "파일 존재함, 크기: ${file.length()} bytes")
//                                    fromFile(file).defaultPage(0).load()
//                                } else {
//                                    Log.e("PdfReadScreen", "파일이 존재하지 않음: ${pdfDetails.filePath}")
//                                    Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
//                                }
//                            }
//                        }
//                    }
//                },
//                modifier = Modifier.fillMaxSize()
//            )
//        } else {
//            // PDF가 준비되지 않은 상태
//            Column(
//                modifier = Modifier.fillMaxSize(),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.Center
//            ) {
//                if (isLoading) {
//                    CircularProgressIndicator()
//                    Spacer(modifier = Modifier.height(16.dp))
//                    Text("PDF 다운로드 중...")
//                    Log.d("PdfReadScreen", "로딩 중 표시")
//                } else if (pdfLoadError != null) {
//                    Text(
//                        text = "오류 발생",
//                        fontSize = 20.sp,
//                        color = Color.Red
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Text(
//                        text = pdfLoadError!!,
//                        fontSize = 16.sp,
//                        textAlign = TextAlign.Center
//                    )
//                    Spacer(modifier = Modifier.height(16.dp))
//                    Button(onClick = { navController.popBackStack() }) {
//                        Text("뒤로가기")
//                    }
//                } else {
//                    Text("PDF 준비 중...")
//                    Log.d("PdfReadScreen", "PDF 준비 중 표시")
//                }
//            }
//        }
//
//        // 페이지 정보 표시 (PDF가 로드된 후)
//        if (showPageInfo && totalPages > 0) {
//            Card(
//                modifier = Modifier
//                    .align(Alignment.TopEnd)
//                    .padding(10.dp),
//                colors = CardDefaults.cardColors(
//                    containerColor = Color.Gray
//                )
//            ) {
//                Text(
//                    text = "$currentPage/$totalPages",
//                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
//                    fontSize = 17.sp,
//                    fontFamily = JakartaSansSemiBold,
//                    color = Color.White
//                )
//            }
//
//            LaunchedEffect(currentPage) {
//                kotlinx.coroutines.delay(3000)
//                showPageInfo = false
//            }
//        }
//    }
//
//    // 에러 상태 표시
//    if (pdfLoadError != null) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Center
//        ) {
//            Text(
//                text = "오류 발생",
//                fontSize = 20.sp,
//                color = Color.Red
//            )
//            Spacer(modifier = Modifier.height(8.dp))
//            Text(
//                text = pdfLoadError!!,
//                fontSize = 16.sp,
//                textAlign = TextAlign.Center
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//            Button(
//                onClick = { navController.popBackStack() }
//            ) {
//                Text("뒤로가기")
//            }
//        }
//        return
//    }
//
//    // Main PDF Reader UI
//    Column(
//        modifier = Modifier.fillMaxSize()
//    ) {
//        // Top Bar
//        CustomTopAppBar(
//            title = pdfTitle,
//            navController = navController
//        )
//
//        // PDF Content
//        Box(
//            modifier = Modifier.fillMaxSize()
//        ) {
//            // PDF View
//            AndroidView(
//                factory = { context ->
//                    Log.d("PdfReadScreen", "AndroidView factory 시작")
//                    PDFView(context, null).apply {
//                        pdfView = this
//                        attachCoroutineScope(pdfRenderScope)
//                        setListener(object : PDFView.Listener {
//                            override fun onPreparationStarted() {
//                                Log.d("PdfReadScreen", "PDF 준비 시작")
//                                isLoading = true
//                            }
//
//                            override fun onPreparationSuccess() {
//                                Log.d("PdfReadScreen", "PDF 준비 성공")
//                                isLoading = false
//                                totalPages = getTotalPage()
//                                Log.d("PdfReadScreen", "총 페이지: $totalPages")
//                            }
//
//                            override fun onPreparationFailed(error: String, e: Exception?) {
//                                Log.e("PdfReadScreen", "PDF 준비 실패: $error", e)
//                                isLoading = false
//                                Toast.makeText(context, "PDF 로딩 실패: $error", Toast.LENGTH_SHORT).show()
//                            }
//
//                            override fun onPageChanged(pageIndex: Int, paginationPageIndex: Int) {
//                                Log.d("PdfReadScreen", "페이지 변경: ${pageIndex + 1}")
//                                currentPage = pageIndex + 1
//                                showPageInfo = true
//                            }
//
//                            override fun onTextSelected(selection: TextSelectionData, rawPoint: PointF) {}
//                            override fun hideTextSelectionOptionWindow() {}
//                            override fun onTextSelectionCleared() {}
//                            override fun onNotesStampsClicked(comments: List<CommentModel>, pointOfNote: PointF) {}
//                            override fun loadTopPdfChunk(mergeId: Int, pageIndexToLoad: Int) {}
//                            override fun loadBottomPdfChunk(mergedId: Int, pageIndexToLoad: Int) {}
//                            override fun onScrolling() {}
//                            override fun onTap() {}
//                            override fun onMergeStart(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                            override fun onMergeEnd(mergeId: Int, mergeType: PdfFile.MergeType) {}
//                            override fun onMergeFailed(mergeId: Int, mergeType: PdfFile.MergeType, message: String, exception: java.lang.Exception?) {}
//                        })
//
//                        // PDFView 로딩 부분
//                        Log.d("PdfReadScreen", "PDF 로딩 시작 - pdfInputStream: ${pdfInputStream != null}, pdfDetails: ${pdfDetails != null}")
//
//                        when {
//                            pdfInputStream != null -> {
//                                Log.d("PdfReadScreen", "그룹 PDF 스트림 로딩")
//                                try {
//                                    val tempFile = File.createTempFile("group_pdf", ".pdf", context.cacheDir)
//                                    Log.d("PdfReadScreen", "임시 파일 생성: ${tempFile.absolutePath}")
//
//                                    tempFile.outputStream().use { output ->
//                                        pdfInputStream!!.copyTo(output)
//                                    }
//
//                                    Log.d("PdfReadScreen", "임시 파일 크기: ${tempFile.length()} bytes")
//                                    fromFile(tempFile).defaultPage(0).load()
//                                    Log.d("PdfReadScreen", "fromFile().load() 호출 완료")
//                                } catch (e: Exception) {
//                                    Log.e("PdfReadScreen", "그룹 PDF 로딩 실패", e)
//                                    Toast.makeText(context, "PDF 로딩 실패: ${e.message}", Toast.LENGTH_SHORT).show()
//                                }
//                            }
//                            pdfDetails?.filePath != null -> {
//                                Log.d("PdfReadScreen", "로컬 PDF 파일 로딩: ${pdfDetails.filePath}")
//                                val file = File(pdfDetails.filePath)
//                                if (file.exists()) {
//                                    Log.d("PdfReadScreen", "파일 존재함, 크기: ${file.length()} bytes")
//                                    fromFile(file).defaultPage(0).load()
//                                } else {
//                                    Log.e("PdfReadScreen", "파일이 존재하지 않음: ${pdfDetails.filePath}")
//                                    Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
//                                }
//                            }
//                            else -> {
//                                Log.e("PdfReadScreen", "PDF 소스가 없음")
//                                Toast.makeText(context, "PDF source not available", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    }
//                },
//                modifier = Modifier.fillMaxSize()
//            )
//
//            // Loading indicator
//            if (isLoading) {
//                CircularProgressIndicator(
//                    modifier = Modifier.align(Alignment.Center)
//                )
//            }
//
//            // 그룹 PDF의 경우 페이지 정보만 표시
//            if (showPageInfo) {
//                Card(
//                    modifier = Modifier
//                        .align(Alignment.TopEnd)
//                        .padding(10.dp),
//                    colors = CardDefaults.cardColors(
//                        containerColor = Color.Gray
//                    )
//                ) {
//                    Text(
//                        text = "$currentPage/$totalPages",
//                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
//                        fontSize = 17.sp,
//                        fontFamily = JakartaSansSemiBold,
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
