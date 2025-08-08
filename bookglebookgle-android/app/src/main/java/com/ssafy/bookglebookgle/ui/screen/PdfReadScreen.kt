package com.ssafy.bookglebookgle.ui.screen

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.PdfAnnotationModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.TextSelectionData
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.selection.TextSelectionOptionsWindow
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.util.FitPolicy
import com.ssafy.bookglebookgle.ui.component.CustomTopAppBar
import com.ssafy.bookglebookgle.ui.theme.BaseColor
import com.ssafy.bookglebookgle.viewmodel.PdfViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.draw.clip
import com.ssafy.bookglebookgle.entity.Participant
import kotlinx.coroutines.delay


@Composable
fun PdfReadScreen(
    groupId: Long? = null,
    userId: String,
    navController: NavHostController,
    viewModel: PdfViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // PDF 뷰어 참조 및 렌더링 스코프
    var pdfView by remember { mutableStateOf<PDFView?>(null) }

    var textSelectionData by remember { mutableStateOf<TextSelectionData?>(null) }
    val pdfRenderScope = remember { CoroutineScope(Dispatchers.IO) }

    // PDF 렌더링 상태
    val isPdfRenderingComplete by viewModel.isPdfRenderingComplete.collectAsState()
    val pdfRenderingError by viewModel.pdfRenderingError.collectAsState()

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
//    val addBookmarkState by viewModel.addBookmarkResponse.state.observeAsState()
//    val deleteBookmarkState by viewModel.deleteBookmarkResponse.state.observeAsState()

    val bookmarkedIcon = painterResource(id = R.drawable.ic_bookmarked)
    val bookmarkIcon = painterResource(id = R.drawable.ic_bookmark)

    val hasBookmark = annotations.bookmarks.any { it.page == currentPage }
    val currentBookmarkIcon = if (hasBookmark) bookmarkedIcon else bookmarkIcon

    val syncConnected by viewModel.syncConnected.collectAsState()
    val syncError by viewModel.syncError.collectAsState()


    var showParticipantsSheet by remember { mutableStateOf(false) }
    var pendingTransferUserId by remember { mutableStateOf<String?>(null) } // 확인 다이얼로그용

    val selectedFilters by viewModel.highlightFilterUserIds.collectAsState()

    //grpc

    // 1) 네비에서 넘어온 isHost 플래그
    val isHostFromNav = navController.previousBackStackEntry
        ?.savedStateHandle
        ?.get<Boolean>("isHost") ?: false

    // 2) 첫 진입 시 사용자·그룹 정보 세팅
    LaunchedEffect(groupId, userId, isHostFromNav) {
        groupId?.let { gid ->
            // 0) 기존 PDF 상태 초기화 & 로드
            Log.d("PdfReadScreen", "==== LaunchedEffect 시작 ====")
            viewModel.resetPdfState()
            viewModel.setGroupId(gid)
            viewModel.loadGroupPdf(gid, context)

            // 1) 사용자·그룹·방장 정보 세팅
            viewModel.setUserInfo(
                userId        = userId,
                groupId       = gid,
                isHostFromNav = isHostFromNav
            )
            // 3) gRPC 동기화 연결
            Log.d("PdfReadScreen", "gRPC 동기화 연결: groupId=$gid, userId=$userId")
            viewModel.connectToSync(gid, userId)
        }
    }


    // 3) 화면 떠날 때(퇴장) 자동 위임 + gRPC 연결 해제
    DisposableEffect(Unit) {
        onDispose {
            viewModel.leaveSyncRoom()
        }
    }

    // 4) ViewModel state 가져오기
    val isCurrentLeader by viewModel.isCurrentLeader.collectAsState()
    val participants    by viewModel.participants.collectAsState()

    //thumbnail
    val thumbnails by viewModel.thumbnails.collectAsState()
    val showThumbnails = remember { mutableStateOf(false) }

    // 헬퍼 함수들
    fun hideTextSelection() {
        viewModel.hideTextSelection()
        textSelectionData = null
        try {
            pdfView?.clearAllTextSelectionAndCoordinates()
        } catch (e: Exception) {
        }
    }

    fun showAddCommentDialog(snippet: String, page: Int, coordinates: Coordinates) {
        Log.d("PdfReadScreen", "==== 댓글 다이얼로그 표시 ====")
        Log.d("PdfReadScreen", "스니펫: $snippet")
        Log.d("PdfReadScreen", "페이지: $page")
        Log.d("PdfReadScreen", "좌표: startX=${coordinates.startX}, startY=${coordinates.startY}, endX=${coordinates.endX}, endY=${coordinates.endY}")

        commentDialogData = Triple(snippet, page, coordinates)
        commentText = ""
        showCommentDialog = true
    }

    // 텍스트 선택 옵션 창
    val textSelectionOptionsWindow = remember {
        TextSelectionOptionsWindow(
            context,
            object : TextSelectionOptionsWindow.Listener {
                override fun onAddHighlightClick(
                    snippet: String,
                    color: String,
                    page: Int,
                    coordinates: Coordinates
                ) {
                    Log.d("PdfReadScreen", "==== 하이라이트 추가 클릭 ====")
                    Log.d("PdfReadScreen", "색상: $color, 페이지: $page, 스니펫: $snippet")
                    viewModel.addHighlight(snippet, color, page, coordinates)
                    hideTextSelection()
                }

                override fun onAddNotClick(snippet: String, page: Int, coordinates: Coordinates) {
                    Log.d("PdfReadScreen", "==== 댓글 추가 클릭 ====")
                    Log.d("PdfReadScreen", "페이지: $page, 스니펫: $snippet")
                    showAddCommentDialog(snippet, page, coordinates)
                    hideTextSelection()
                }
            }
        )
    }



    // 댓글 추가 결과 처리
    LaunchedEffect(addCommentState) {
        Log.d("PdfReadScreen", "==== addCommentState 변경 감지 ====")
        Log.d("PdfReadScreen", "상태: $addCommentState")

        when (addCommentState) {
            is ResponseState.Success<*> -> {
                val comment = (addCommentState as ResponseState.Success<*>).response as? CommentModel
                comment?.let {
                    Log.d("PdfReadScreen", "댓글 추가 성공 - PDF 뷰에 추가")
                    Log.d("PdfReadScreen", "추가된 댓글: $it")
                    pdfView?.addComment(it)
                } ?: Log.e("PdfReadScreen", "댓글 캐스팅 실패")
            }
            is ResponseState.Failed -> {
                val error = (addCommentState as ResponseState.Failed).error
                Log.e("PdfReadScreen", "댓글 추가 실패: $error")
            }
            is ResponseState.Loading -> {
                Log.d("PdfReadScreen", "댓글 추가 중...")
            }
            else -> {
                Log.d("PdfReadScreen", "addCommentState - 기타 상태")
            }
        }
    }

    // 하이라이트 추가 결과 처리
    LaunchedEffect(addHighlightState) {
        Log.d("PdfReadScreen", "==== addHighlightState 변경 감지 ====")
        Log.d("PdfReadScreen", "상태: $addHighlightState")

        when (addHighlightState) {
            is ResponseState.Success<*> -> {
                val highlight = (addHighlightState as ResponseState.Success<*>).response as? HighlightModel
                highlight?.let {
                    Log.d("PdfReadScreen", "하이라이트 추가 성공 - PDF 뷰에 추가")
                    Log.d("PdfReadScreen", "추가된 하이라이트: $it")
                    pdfView?.addHighlight(it)
                } ?: Log.e("PdfReadScreen", "하이라이트 캐스팅 실패")
            }
            is ResponseState.Failed -> {
                val error = (addHighlightState as ResponseState.Failed).error
                Log.e("PdfReadScreen", "하이라이트 추가 실패: $error")
            }
            is ResponseState.Loading -> {
                Log.d("PdfReadScreen", "하이라이트 추가 중...")
            }
            else -> {
                Log.d("PdfReadScreen", "addHighlightState - 기타 상태")
            }
        }
    }

    LaunchedEffect(isPdfRenderingComplete) {
        if (isPdfRenderingComplete) {
            // PDF 로딩이 완료되면 현재 페이지를 중앙에 위치
            kotlinx.coroutines.delay(100) // 렌더링 완료 후 잠시 대기
            pdfView?.centerCurrentPage(withAnimation = false)
        }
    }

// Compose
    LaunchedEffect(isCurrentLeader) {
        pdfView?.isSwipeEnabled = isCurrentLeader
        pdfView?.setPageFling(isCurrentLeader)
        pdfView?.centerCurrentPage(withAnimation = false)
    }



//    LaunchedEffect(currentPage) {
//        if (pdfView != null && isPdfRenderingComplete) {
//            // 페이지가 변경될 때마다 중앙 정렬 (필요한 경우에만)
//            pdfView?.centerCurrentPage(withAnimation = true)
//        }
//    }

//    LaunchedEffect(addBookmarkState) {
//        when (addBookmarkState) {
//            is ResponseState.Success<*> -> {
//                // 북마크 추가 성공 시 UI 업데이트 처리가 없음
//                viewModel.loadAllAnnotations()
//                Log.d("PdfReadScreen", "북마크 추가 성공 - 페이지: $currentPage")
//            }
//
//            is ResponseState.Failed -> {
//                Log.d(
//                    "PdfReadScreen",
//                    "북마크 추가 실패: ${(addBookmarkState as ResponseState.Failed).error}"
//                )
//            }
//
//            else -> {}
//        }
//    }

//    LaunchedEffect(deleteBookmarkState) {
//        when (deleteBookmarkState) {
//            is ResponseState.Success<*> -> {
//                viewModel.loadAllAnnotations()
//                Log.d("PdfReadScreen", "북마크 삭제 성공 - 페이지: $currentPage")
//            }
//
//            is ResponseState.Failed -> {
//                Log.e(
//                    "PdfReadScreen",
//                    "북마크 삭제 실패: ${(deleteBookmarkState as ResponseState.Failed).error}"
//                )
//            }
//
//            else -> {}
//        }
//    }

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

    Scaffold(
        topBar = {
            // Top Bar
            CustomTopAppBar(
                title = pdfTitle,
                navController = navController,
                isPdfView = true,
                onParticipantsClick = { showParticipantsSheet = true }
            )
        }
    ) { paddingValues ->
        // Content
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            ) {
            // 기존의 when 문을 다음과 같이 수정하세요

            when {
                // PDF 다운로드 중
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = BaseColor,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("PDF 다운로드 중...")
                    }
                }

                // PDF 로드 에러
                pdfLoadError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("PDF 로드 오류", fontSize = 24.sp, color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(pdfLoadError!!, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }

                // PDF 렌더링 에러
                pdfRenderingError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("PDF 렌더링 오류", fontSize = 24.sp, color = Color.Red)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(pdfRenderingError!!, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }

                // PDF 파일이 준비되면 AndroidView 생성 (렌더링 완료 여부와 관계없이)
                pdfReady && pdfFile != null -> {
                    Log.d("PdfReadScreen", "PDF AndroidView 생성 - 파일: ${pdfFile!!.absolutePath}")

                    // 렌더링이 완료되지 않은 경우 로딩 오버레이 표시
                    Box(modifier = Modifier.fillMaxSize()) {
                        key(pdfFile) {
                            // 1) PDFView 인스턴스를 한번만 생성
                            val rememberedPdfView = remember {
                                Log.d("PdfReadScreen", "AndroidView factory 실행")

                                PDFView(context, null).apply {
                                    pdfView = this
                                    viewModel.setPdfView(this)
                                    attachCoroutineScope(pdfRenderScope)

                                    // 텍스트 선택 옵션 창 연결
                                    textSelectionOptionsWindow.attachToPdfView(this)

                                    setListener(object : PDFView.Listener {
                                        override fun onPreparationStarted() {
                                            Log.d("PdfReadScreen", "PDF 렌더링 시작 - Listener 호출됨")
                                            viewModel.onPdfRenderingStarted()
                                        }

                                        override fun onPreparationSuccess() {
                                            Log.d("PdfReadScreen", "PDF 렌더링 성공 - Listener 호출됨")
                                            Log.d("PdfReadScreen", "총 페이지: ${getTotalPage()}")

                                            viewModel.updateTotalPages(getTotalPage())
                                            viewModel.onPdfRenderingSuccess()

                                            // 기존 주석들 로드
                                            viewModel.loadAllAnnotations()
                                            viewModel.getPage()
                                        }

                                        override fun onPreparationFailed(
                                            error: String,
                                            e: Exception?
                                        ) {
                                            Log.e(
                                                "PdfReadScreen",
                                                "PDF 렌더링 실패 - Listener 호출됨: $error",
                                                e
                                            )
                                            viewModel.onPdfRenderingFailed(error)
                                        }

                                        override fun onPageChanged(
                                            pageIndex: Int,
                                            paginationPageIndex: Int
                                        ) {
                                            val newPage = pageIndex + 1
                                            Log.d(
                                                "PdfReadScreen",
                                                "[onPageChanged] newPage=$newPage,"
                                            )
                                            if (viewModel.currentPage.value != newPage) {
                                                viewModel.updateCurrentPage(newPage)
                                                Log.d("PdfReadScreen", "페이지 변경: $newPage")
                                            }

                                            // gRPC 전송 조건을 철저히 확인 (루프 방지용)
                                            viewModel.notifyPageChange(newPage)
                                        }


                                        override fun onTap() {
                                            hideTextSelection()
                                            textSelectionOptionsWindow.dismiss(false)
                                            showThumbnails.value = !showThumbnails.value
                                        }

                                        override fun onTextSelected(
                                            selection: TextSelectionData,
                                            rawPoint: PointF
                                        ) {
                                            textSelectionData = selection
                                            viewModel.showTextSelection(rawPoint)
                                            textSelectionOptionsWindow.show(
                                                rawPoint.x,
                                                rawPoint.y,
                                                selection
                                            )
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

                                        override fun onNotesStampsClicked(
                                            comments: List<CommentModel>,
                                            pointOfNote: PointF
                                        ) {
                                            viewModel.showCommentPopup(comments, pointOfNote)
                                            Log.d("PdfReadScreen", "댓글 클릭: ${comments.size}개")
                                        }

                                        override fun loadTopPdfChunk(
                                            mergeId: Int,
                                            pageIndexToLoad: Int
                                        ) {
                                        }

                                        override fun loadBottomPdfChunk(
                                            mergedId: Int,
                                            pageIndexToLoad: Int
                                        ) {
                                        }

                                        override fun onScrolling() {
                                            hideTextSelection()
                                            textSelectionOptionsWindow.dismiss(false)
                                        }

                                        override fun onMergeStart(
                                            mergeId: Int,
                                            mergeType: PdfFile.MergeType
                                        ) {
                                        }

                                        override fun onMergeEnd(
                                            mergeId: Int,
                                            mergeType: PdfFile.MergeType
                                        ) {
                                        }

                                        override fun onMergeFailed(
                                            mergeId: Int,
                                            mergeType: PdfFile.MergeType,
                                            message: String,
                                            exception: java.lang.Exception?
                                        ) {
                                        }
                                    })

                                    // PDF 로드
                                    Log.d(
                                        "PdfReadScreen",
                                        "PDF 파일 로드 시작: ${pdfFile!!.absolutePath}"
                                    )
                                    Log.d("PdfReadScreen", "PDF 파일 존재 여부: ${pdfFile!!.exists()}")
                                    Log.d("PdfReadScreen", "PDF 파일 크기: ${pdfFile!!.length()} bytes")

                                    try {
                                        fromFile(pdfFile!!)
                                            .defaultPage(0)
                                            .enableSwipe(isCurrentLeader)              // 스와이프 비활성화
                                            .swipeHorizontal(false)         // 수평 스와이프 비활성화
                                            .pageSnap(true)                 // 페이지 스냅 활성화
                                            .pageFitPolicy(FitPolicy.WIDTH) // 페이지를 화면 너비에 맞춤
                                            .fitEachPage(true)              // 각 페이지를 개별적으로 맞춤
                                            .enableDoubleTap(true)          // 더블탭 줌 활성화
                                            .autoSpacing(true)
                                            .pageFling(isCurrentLeader)
                                            .load()
                                        Log.d("PdfReadScreen", "PDF 파일 로드 호출 완료")
                                    } catch (e: Exception) {
                                        Log.e("PdfReadScreen", "PDF 파일 로드 중 예외 발생", e)
                                        viewModel.onPdfRenderingFailed("PDF 로드 중 예외: ${e.message}")
                                    }
                                }
                            }

                            AndroidView(
                                factory = { rememberedPdfView.also { viewModel.setPdfView(it) } },
                                update = { view ->
                                    textSelectionOptionsWindow.attachToPdfView(view)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 렌더링이 완료되지 않은 경우 로딩 오버레이 표시
                        if (!isPdfRenderingComplete) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = BaseColor,
                                        strokeWidth = 4.dp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("PDF 불러오는 중...")
                                }
                            }
                        }
                    }
                }

                // PDF 파일이 없는 경우
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("PDF를 준비하는 중...", fontSize = 16.sp)
                    }
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
                                    placeholder = {
                                        Text(
                                            "댓글을 입력하세요",
                                            color = Color.Gray,
                                            fontSize = 14.sp
                                        )
                                    },
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
                                    Log.d(
                                        "PdfReadScreen",
                                        "- coordinates: startX=${coordinates.startX}, startY=${coordinates.startY}, endX=${coordinates.endX}, endY=${coordinates.endY}"
                                    )

                                    viewModel.addComment(
                                        snippet,
                                        commentText.trim(),
                                        page,
                                        coordinates
                                    )

                                    showCommentDialog = false
                                    commentDialogData = null
                                    commentText = ""

                                    Log.d("PdfReadScreen", "댓글 추가 요청 완료")
                                }
                            },
                            enabled = commentText.trim().isNotEmpty()
                        ) {
                            Text(
                                "저장",
                                color = if (commentText.trim()
                                        .isNotEmpty()
                                ) BaseColor else Color.Gray
                            )
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


            AnimatedVisibility(
                visible = showThumbnails.value,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ThumbnailBottomSheet(
                    thumbnails = thumbnails,
                    currentPage = currentPage,
                    onThumbnailClick = { page ->
                        if(isCurrentLeader){viewModel.goToPage(page + 1) }},
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 참가자 목록 바텀시트
            if (showParticipantsSheet) {
                ParticipantsBottomSheet(
                    currentUserId = userId,
                    isCurrentLeader = isCurrentLeader,
                    participants = participants,
                    selectedFilterUserIds = selectedFilters,           // <- Set 전달
                    onDismiss = { showParticipantsSheet = false },
                    onTransferClick = { targetId ->
                        if (targetId != userId && isCurrentLeader) {
                            pendingTransferUserId = targetId
                        } else if (!isCurrentLeader) {
                            // 리더만 가능
                            android.widget.Toast
                                .makeText(context, "리더만 권한을 이양할 수 있어요.", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        } },
                    onToggleFilter = { id -> viewModel.toggleHighlightFilterUser(id) }, // <- 토글
                    onClearFilter = { viewModel.clearHighlightFilter() }                // <- 전체
                )
            }

// 권한 이양 확인 다이얼로그
            if (pendingTransferUserId != null) {
                AlertDialog(
                    onDismissRequest = { pendingTransferUserId = null },
                    title = { Text("리더 이양") },
                    text = { Text("정말 이 참가자에게 리더를 넘길까요?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.transferLeadershipToUser(pendingTransferUserId!!) // 서버에 이양 요청
                            pendingTransferUserId = null
                            showParticipantsSheet = false
                        }) { Text("이양") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingTransferUserId = null }) { Text("취소") }
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

@Composable
fun ThumbnailBottomSheet(
    thumbnails: List<Bitmap>,
    currentPage: Int,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = Color.White.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(thumbnails) { index, bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${index + 1}",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onThumbnailClick(index)
                        }
                        .border(
                            width = if (currentPage == index + 1) 2.dp else 0.dp,
                            color = if (currentPage == index + 1) BaseColor else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantsBottomSheet(
    currentUserId: String,
    isCurrentLeader: Boolean,
    participants: List<Participant>,
    selectedFilterUserIds: Set<String>,                 // <- Set
    onDismiss: () -> Unit,
    onTransferClick: (String) -> Unit,
    onToggleFilter: (String) -> Unit,                   // <- 여러명 토글
    onClearFilter: () -> Unit                           // <- 전체 해제
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 정렬: 나 → 리더(내가 아니면) → 이름/아이디 순
    val sorted = remember(participants, currentUserId) {
        participants.sortedWith(
            compareByDescending<Participant> { it.userId == currentUserId }
                .thenByDescending { it.isCurrentHost }
                .thenBy { p -> p.userName.ifBlank { "사용자${p.userId}" } } //
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ==== 필터 칩 (시트 안으로 이동) ====
            LazyRow(
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SimpleFilterChip(
                        label = "전체",
                        selected = selectedFilterUserIds.isEmpty(),
                        onClick = onClearFilter
                    )
                }
                items(participants.size) { i ->
                    val p = participants[i]
                    SimpleFilterChip(
                        label = p.userName.ifBlank { p.userId },
                        selected = p.userId in selectedFilterUserIds,
                        onClick = { onToggleFilter(p.userId) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "참여자 (${participants.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(Modifier.height(4.dp))

            sorted.forEach { p ->
                ParticipantRow(
                    me = (p.userId == currentUserId),
                    isLeader = p.isCurrentHost,
                    userName = p.userName.ifBlank { p.userId },
                    onClick = {
                        if (p.userId != currentUserId) onTransferClick(p.userId)
                    },
                    enabled = isCurrentLeader && p.userId != currentUserId
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SimpleFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFFE0ECFF) else Color(0xFFEFF3F7)
    val fg = if (selected) BaseColor else Color(0xFF334155)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun ParticipantRow(
    me: Boolean,
    isLeader: Boolean,
    userName: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderWidth = if (isLeader) 2.dp else 0.dp
    val borderColor = if (isLeader) BaseColor else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 아바타(이니셜) + 리더 테두리
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(borderWidth, borderColor, CircleShape)
                .background(Color(0xFFEFEFEF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "🙂",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = userName,
                    fontWeight = if (me) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp
                )
                if (me) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(text = "나")
                }
                if (isLeader) {
                    Spacer(Modifier.width(6.dp))
                    AssistChip(text = "리더")
                }
            }
            if (!enabled && !me && !isLeader) {
                Text("리더만 이양할 수 있어요", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AssistChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFF1F5F9))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
    }
}