package com.ssafy.bookglebookgle.ui.screen

import android.graphics.PointF
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ssafy.bookglebookgle.R
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.state.ResponseState
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PdfFile
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.PdfAnnotationModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.TextSelectionData
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.selection.TextSelectionOptionsWindow
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.viewmodel.PdfViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun PdfReadScreen(
    groupId: Long? = null,
    viewModel: PdfViewModel = hiltViewModel(),
    navController: NavHostController
) {
    val context = LocalContext.current

    // PDF 뷰어 참조 및 렌더링 스코프
    var pdfView by remember { mutableStateOf<PDFView?>(null) }
    var textSelectionData by remember { mutableStateOf<TextSelectionData?>(null) }
    val pdfRenderScope = remember { CoroutineScope(Dispatchers.IO) }

    // ViewModel 상태들 관찰
    val pdfFile by viewModel.pdfFile.collectAsState()
    val pdfTitle by viewModel.pdfTitle.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pdfLoadError by viewModel.pdfLoadError.collectAsState()
    val pdfReady by viewModel.pdfReady.collectAsState()

    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val showPageInfo by viewModel.showPageInfo.collectAsState()

    val showTextSelectionOptions by viewModel.showTextSelectionOptions.collectAsState()
    val textSelectionPoint by viewModel.textSelectionPoint.collectAsState()
    val selectedComments by viewModel.selectedComments.collectAsState()
    val commentPopupPoint by viewModel.commentPopupPoint.collectAsState()
    val showCommentPopup by viewModel.showCommentPopup.collectAsState()

    // 댓글 다이얼로그 상태 추가
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentDialogData by remember { mutableStateOf<Triple<String, Int, Coordinates>?>(null) }
    var commentText by remember { mutableStateOf("") }

    val annotations by viewModel.annotations.collectAsState()

    // 상태 핸들러들 관찰
    val addCommentState by viewModel.addCommentResponse.state.observeAsState()
    val addHighlightState by viewModel.addHighlightResponse.state.observeAsState()
    val addBookmarkState by viewModel.addBookmarkResponse.state.observeAsState()
    val deleteBookmarkState by viewModel.deleteBookmarkResponse.state.observeAsState()

    // 북마크 아이콘 결정
    val bookmarkIcon = if (viewModel.hasBookmarkOnPage(currentPage)) {
        painterResource(id = R.drawable.ic_bookmarked)
    } else {
        painterResource(id = R.drawable.ic_bookmark)
    }

    // 헬퍼 함수들
    fun hideTextSelection() {
        viewModel.hideTextSelection()
        textSelectionData = null
        try {
            pdfView?.clearAllTextSelectionAndCoordinates()
        } catch (e: Exception) {
            Log.e("PdfReadScreen", "hideTextSelection 오류: ${e.message}", e)
        }
    }

    fun showAddCommentDialog(snippet: String, page: Int, coordinates: Coordinates) {
        Log.d("PdfReadScreen", "댓글 다이얼로그 표시 - snippet: $snippet, page: $page")
        Log.d("PdfReadScreen", "좌표 정보 - startX: ${coordinates.startX}, startY: ${coordinates.startY}, endX: ${coordinates.endX}, endY: ${coordinates.endY}")
        commentDialogData = Triple(snippet, page, coordinates)
        commentText = ""
        showCommentDialog = true
    }

    // 텍스트 선택 옵션 창
    val textSelectionOptionsWindow = remember {
        TextSelectionOptionsWindow(
            context,
            object : TextSelectionOptionsWindow.Listener {
                override fun onAddHighlightClick(snippet: String, color: String, page: Int, coordinates: Coordinates) {
                    viewModel.addHighlight(snippet, color, page, coordinates)
                    hideTextSelection()
                }

                override fun onAddNotClick(snippet: String, page: Int, coordinates: Coordinates) {
                    showAddCommentDialog(snippet, page, coordinates)
                    hideTextSelection()
                }
            }
        )
    }

    // ViewModel 초기 설정 - 뷰모델이 모든 상태를 관리
    LaunchedEffect(groupId) {
        viewModel.resetPdfState()
        groupId?.let {
            viewModel.setGroupId(it)
            viewModel.loadGroupPdf(it, context)
        }
    }

    // 주석 추가/삭제 결과 처리
    LaunchedEffect(addCommentState) {
        when (addCommentState) {
            is ResponseState.Success<*> -> {
                val comment = (addCommentState as ResponseState.Success<*>).response as? CommentModel
                comment?.let { pdfView?.addComment(it) }
            }
            is ResponseState.Failed -> {
                Log.e("PdfReadScreen", "댓글 추가 실패: ${(addCommentState as ResponseState.Failed).error}")
            }
            else -> {}
        }
    }

    LaunchedEffect(addHighlightState) {
        when (addHighlightState) {
            is ResponseState.Success<*> -> {
                val highlight = (addHighlightState as ResponseState.Success<*>).response as? com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
                highlight?.let { pdfView?.addHighlight(it) }
            }
            is ResponseState.Failed -> {
                Log.e("PdfReadScreen", "하이라이트 추가 실패: ${(addHighlightState as ResponseState.Failed).error}")
            }
            else -> {}
        }
    }

    // 페이지 정보 자동 숨김
    LaunchedEffect(showPageInfo) {
        if (showPageInfo) {
            kotlinx.coroutines.delay(3000)
            viewModel.hidePageInfo()
        }
    }

    // 댓글 팝업 자동 숨김
    LaunchedEffect(showCommentPopup) {
        if (showCommentPopup) {
            kotlinx.coroutines.delay(3000)
            viewModel.hideCommentPopup()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        CustomTopAppBar(
            title = pdfTitle,
            navController = navController,
            actions = {
                // 북마크 토글 버튼
                IconButton(onClick = { viewModel.toggleBookmark(currentPage) }) {
                    Icon(
                        painter = bookmarkIcon,
                        contentDescription = "북마크"
                    )
                }
            }
        )

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
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

                                // 텍스트 선택 옵션 창 연결
                                textSelectionOptionsWindow.attachToPdfView(this)

                                setListener(object : PDFView.Listener {
                                    override fun onPreparationStarted() {
                                        Log.d("PdfReadScreen", "PDF 렌더링 시작")
                                    }

                                    override fun onPreparationSuccess() {
                                        Log.d("PdfReadScreen", "PDF 렌더링 성공")
                                        viewModel.updateTotalPages(getTotalPage())
                                        Log.d("PdfReadScreen", "총 페이지: ${getTotalPage()}")

                                        // 기존 주석들 로드
                                        loadAnnotations(annotations.toAnnotationList())
                                    }

                                    override fun onPreparationFailed(error: String, e: Exception?) {
                                        Log.e("PdfReadScreen", "PDF 렌더링 실패: $error", e)
                                    }

                                    override fun onPageChanged(pageIndex: Int, paginationPageIndex: Int) {
                                        viewModel.updateCurrentPage(pageIndex + 1)
                                        Log.d("PdfReadScreen", "페이지 변경: ${pageIndex + 1}")
                                    }

                                    override fun onTextSelected(selection: TextSelectionData, rawPoint: PointF) {
                                        textSelectionData = selection
                                        viewModel.showTextSelection(rawPoint)
                                        textSelectionOptionsWindow.show(rawPoint.x, rawPoint.y, selection)
                                    }

                                    override fun hideTextSelectionOptionWindow() {
                                        hideTextSelection()
                                        textSelectionOptionsWindow.dismiss(false)
                                    }

                                    override fun onTextSelectionCleared() {
                                        viewModel.hideTextSelection()
                                        textSelectionData = null
                                        textSelectionOptionsWindow.dismiss(false)
                                    }

                                    override fun onNotesStampsClicked(comments: List<CommentModel>, pointOfNote: PointF) {
                                        viewModel.showCommentPopup(comments, pointOfNote)
                                        Log.d("PdfReadScreen", "댓글 클릭: ${comments.size}개")
                                    }

                                    override fun loadTopPdfChunk(mergeId: Int, pageIndexToLoad: Int) {
                                        // PDF 청크 로딩 로직 (필요시 구현)
                                    }

                                    override fun loadBottomPdfChunk(mergedId: Int, pageIndexToLoad: Int) {
                                        // PDF 청크 로딩 로직 (필요시 구현)
                                    }

                                    override fun onScrolling() {
                                        hideTextSelection()
                                        textSelectionOptionsWindow.dismiss(false)
                                    }

                                    override fun onTap() {
                                        hideTextSelection()
                                        textSelectionOptionsWindow.dismiss(false)
                                    }

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
            }

            // 텍스트 선택 옵션 창
            if (showTextSelectionOptions) {
                textSelectionOptionsWindow.ComposeContent()
            }

            // 댓글 팝업
            if (showCommentPopup) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "댓글 (${selectedComments.size}개)",
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        selectedComments.forEach { comment ->
                            Text(
                                text = comment.text,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.hideCommentPopup() }
                        ) {
                            Text("닫기")
                        }
                    }
                }
            }

            // 댓글 작성 다이얼로그
            if (showCommentDialog && commentDialogData != null) {
                AlertDialog(
                    onDismissRequest = {
                        showCommentDialog = false
                        commentDialogData = null
                        commentText = ""
                    },
                    title = {
                        Text(
                            text = "댓글 작성",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "선택된 텍스트:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                            Text(
                                text = "\"${commentDialogData!!.first}\"",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                                style = androidx.compose.ui.text.TextStyle(
                                    background = BaseColor.copy(alpha = 0.3f)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CompositionLocalProvider(
                                LocalTextSelectionColors provides TextSelectionColors(
                                    handleColor = BaseColor, // 드래그 핸들(물방울) 색상
                                    backgroundColor = BaseColor.copy(alpha = 0.3f) // 선택 영역 배경색 (투명도 적용)
                                )
                            ) {
                                OutlinedTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    placeholder = { Text("댓글을 입력하세요", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BaseColor,
                                        focusedLabelColor = BaseColor,
                                        cursorColor = BaseColor
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (commentText.trim().isNotEmpty()) {
                                    val (snippet, page, coordinates) = commentDialogData!!

                                    Log.d("PdfReadScreen", "댓글 저장 시작")
                                    Log.d("PdfReadScreen", "- snippet: $snippet")
                                    Log.d("PdfReadScreen", "- commentText: $commentText")
                                    Log.d("PdfReadScreen", "- page: $page")
                                    Log.d("PdfReadScreen", "- coordinates: startX=${coordinates.startX}, startY=${coordinates.startY}, endX=${coordinates.endX}, endY=${coordinates.endY}")

                                    viewModel.addComment(snippet, commentText.trim(), page, coordinates)

                                    showCommentDialog = false
                                    commentDialogData = null
                                    commentText = ""

                                    Log.d("PdfReadScreen", "댓글 추가 요청 완료")
                                }
                            },
                            enabled = commentText.trim().isNotEmpty()
                        ) {
                            Text("저장", color = if (commentText.trim().isNotEmpty()) BaseColor else Color.Gray)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCommentDialog = false
                                commentDialogData = null
                                commentText = ""
                            }
                        ) {
                            Text("취소", color = Color.Gray)
                        }
                    }
                )
            }



        }
    }
}

// 확장 함수: AnnotationListResponse를 PdfAnnotationModel 리스트로 변환
private fun AnnotationListResponse.toAnnotationList(): List<PdfAnnotationModel> {
    val annotationList = mutableListOf<PdfAnnotationModel>()

    // 댓글을 PdfAnnotationModel로 변환
    annotationList.addAll(comments.map { comment ->
        comment.updateAnnotationData()
    })

    // 하이라이트를 PdfAnnotationModel로 변환
    annotationList.addAll(highlights.map { highlight ->
        highlight.updateAnnotationData()
    })

    return annotationList
}