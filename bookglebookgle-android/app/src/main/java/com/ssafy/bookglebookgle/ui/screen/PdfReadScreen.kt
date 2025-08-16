package com.ssafy.bookglebookgle.ui.screen

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ssafy.bookglebookgle.entity.Participant
import com.ssafy.bookglebookgle.viewmodel.GroupDetailViewModel
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.unit.Dp
import com.ssafy.bookglebookgle.util.LottieOverlay
import com.ssafy.bookglebookgle.viewmodel.ChatRoomViewModel

// 키 문자열 ↔ 로컬 드로어블
private val AVATAR_RES_MAP = mapOf(
    "whitebear" to R.drawable.whitebear_no_bg,
    "penguin"   to R.drawable.penguin_no_bg,
    "squirrel"  to R.drawable.squirrel_no_bg,
    "rabbit"    to R.drawable.rabbit_no_bg,
    "dog"       to R.drawable.dog_no_bg,
    "cat"       to R.drawable.cat_no_bg
)
private fun keyToResId(key: String?): Int? = key?.let { AVATAR_RES_MAP[it] }



@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PdfReadScreen(
    groupId: Long? = null,
    userId: String,
    navController: NavHostController,
    viewModel: PdfViewModel = hiltViewModel(),
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val userColors by viewModel.colorByUser.collectAsState()
    val avatarKeys by viewModel.avatarKeyByUser.collectAsState()

    val selectedHighlights by viewModel.selectedHighlights.collectAsState()
    val highlightPopupPoint by viewModel.highlightPopupPoint.collectAsState()
    val showHighlightPopup by viewModel.showHighlightPopup.collectAsState()

    var showChatSheet by remember { mutableStateOf(false) }

    LaunchedEffect(showTextSelectionOptions) {
        pdfView?.apply {
            setUserTransformLocked(showTextSelectionOptions) // 팬/줌 락
            isSwipeEnabled = !showTextSelectionOptions
            setPageFling(!showTextSelectionOptions)
            enableDoubleTap(!showTextSelectionOptions)
            if (showTextSelectionOptions) stopFling()
        }
    }




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

            // PdfReadScreen.kt (LaunchedEffect(groupId, userId, isHostFromNav) 내부, setUserInfo(...) '바로 다음'에 추가)
            val handle = navController.previousBackStackEntry?.savedStateHandle

            val pageCountArg = handle?.get<Int>("pageCount") ?: totalPages
            val membersJson  = handle?.get<String>("initialMembersJson")
            val progressJson = handle?.get<String>("initialProgressJson")

            val gson = Gson()
            val membersType = object : TypeToken<List<GroupDetailViewModel.InitialMember>>() {}.type
            val progressType = object : TypeToken<Map<String, Int>>() {}.type

            val initialMembers: List<GroupDetailViewModel.InitialMember> =
                membersJson?.let { gson.fromJson(it, membersType) } ?: emptyList()
            val initialProgress: Map<String, Int> =
                progressJson?.let { gson.fromJson(it, progressType) } ?: emptyMap()

            viewModel.initFromGroupDetail(
                pageCount = pageCountArg,
                initialMembers = initialMembers,
                initialProgress = initialProgress
            )

            // 3) gRPC 동기화 연결
            Log.d("PdfReadScreen", "gRPC 동기화 연결: groupId=$gid, userId=$userId")
            viewModel.connectToSync(gid, userId)
        }
    }


    // 3) 화면 떠날 때(퇴장) 자동 위임 + gRPC 연결 해제
// PdfReadScreen
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> viewModel.onAppForeground()
                Lifecycle.Event.ON_STOP  -> viewModel.onAppBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

// 네비 스택에서 화면이 완전히 사라질 때만 해제
    DisposableEffect(Unit) {
        onDispose { viewModel.leaveSyncRoom()
        }
    }


    // 4) ViewModel state 가져오기
    val isCurrentLeader by viewModel.isCurrentLeader.collectAsState()
    val participants    by viewModel.participants.collectAsState()

    val readingMode by viewModel.readingMode.collectAsState()
    val myMaxReadPage by viewModel.myMaxReadPage.collectAsState()

    val allowSwipe = remember(isCurrentLeader, readingMode) {
        readingMode == PdfViewModel.ReadingMode.FREE || isCurrentLeader
    }
    val isFollower = remember(isCurrentLeader, readingMode) {
        readingMode == PdfViewModel.ReadingMode.FOLLOW && !isCurrentLeader
    }

    var lastViewportOrScrollTs by remember { mutableStateOf(0L) }
    fun canOpenPopup(): Boolean = SystemClock.elapsedRealtime() - lastViewportOrScrollTs > 200L

    LaunchedEffect(isFollower) {
        pdfView?.apply {
            setUserTransformLocked(isFollower)
            enableDoubleTap(!isFollower)
            isSwipeEnabled = allowSwipe
            setPageFling(allowSwipe)
            if (isFollower) stopFling()
        }
    }



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

    LaunchedEffect(showHighlightPopup) {
        if (showHighlightPopup) {
            kotlinx.coroutines.delay(3000)
            viewModel.hideHighlightPopup()
        }
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
    LaunchedEffect(allowSwipe) {
        pdfView?.isSwipeEnabled = allowSwipe
        pdfView?.setPageFling(allowSwipe)
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
                onParticipantsClick = { showParticipantsSheet = true },
                onChatClick = {
                    // 동시에 두 시트가 겹치지 않도록 처리
                    showParticipantsSheet = false
                    showChatSheet = !showChatSheet
                }
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
                    LottieOverlay(
                        raw = R.raw.quiz_loading,
                        title = "PDF 다운로드 중...",
                        subtitle = "페이지와 텍스트를 준비하고 있어요",
                        color = Color.Transparent,
                        dimAmount = 0f,
                        useCard = false
                    )
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
                                            if (!canOpenPopup()) return
                                            viewModel.showCommentPopup(comments, pointOfNote)
                                            Log.d("PdfReadScreen", "댓글 클릭: ${comments.size}개")
                                        }

                                        override fun onHighlightClicked(highlights: List<HighlightModel>, pointOfHighlight: PointF) {
                                            if (!canOpenPopup()) return
                                            viewModel.showHighlightPopup(highlights, pointOfHighlight)
                                            Log.d("PdfReadScreen", "하이라이트 클릭: ${highlights.size}개")
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
                                            if (!showTextSelectionOptions) {
                                                lastViewportOrScrollTs = SystemClock.elapsedRealtime()
                                                hideTextSelection()
                                                textSelectionOptionsWindow.dismiss(false)
                                            }
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

                                        override fun onViewportChanged(
                                            pageIndex: Int,
                                            fitWidthZoom: Float,
                                            currentZoom: Float,
                                            centerXNorm: Float,
                                            centerYNorm: Float
                                        ) {
                                            lastViewportOrScrollTs = SystemClock.elapsedRealtime()
                                            viewModel.onViewportChangedFromUi(
                                                pageIndex0 = pageIndex,
                                                fitWidthZoom = fitWidthZoom,
                                                currentZoom = currentZoom,
                                                centerXNorm = centerXNorm,
                                                centerYNorm = centerYNorm
                                            )
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
                                            .enableSwipe(allowSwipe)              // 스와이프 비활성화
                                            .swipeHorizontal(false)         // 수평 스와이프 비활성화
                                            .pageSnap(true)                 // 페이지 스냅 활성화
                                            .pageFitPolicy(FitPolicy.WIDTH) // 페이지를 화면 너비에 맞춤
                                            .fitEachPage(true)              // 각 페이지를 개별적으로 맞춤
                                            .enableDoubleTap(!isFollower)          // 더블탭 줌 활성화
                                            .autoSpacing(true)
                                            .pageFling(allowSwipe)
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
                                    view.setUserTransformLocked(isFollower)
                                    view.enableDoubleTap(!isFollower)
                                    view.isSwipeEnabled = allowSwipe
                                    view.setPageFling(allowSwipe)
                                    if (isFollower) view.stopFling()

                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInteropFilter { ev ->
                                        if (!isFollower) return@pointerInteropFilter false
                                        when (ev.actionMasked) {
                                            MotionEvent.ACTION_POINTER_DOWN, // 핀치 시작
                                            MotionEvent.ACTION_POINTER_UP,   // 핀치 종료(여유)
                                            MotionEvent.ACTION_MOVE -> true  // 드래그/패닝 막기
                                            else -> false                    // 탭/클릭은 통과
                                        }
                                    }
                            )
                        }

                        // 렌더링이 완료되지 않은 경우 로딩 오버레이 표시
                        if (!isPdfRenderingComplete) {
                            LottieOverlay(
                                raw = R.raw.quiz_loading,           // pdf용 json 따로 두면 더 좋아요
                                title = "PDF 불러오는 중...",
                                subtitle = "페이지와 텍스트를 준비하고 있어요",
                                color = Color.Transparent,
                                dimAmount = 0f,
                                useCard = false
                            )
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

            if (showCommentPopup && selectedComments.isNotEmpty()) {
                CommentPopupDialogMini(
                    comments = selectedComments,                // distinctBy(id) 적용됨
                    currentUserId = userId,
                    onDelete = { id -> viewModel.deleteComment(id); viewModel.hideCommentPopup() },
                    onDismiss = { viewModel.hideCommentPopup()
                        pdfView?.dismissSelection()
                    }
                )
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
                        if (readingMode == PdfViewModel.ReadingMode.FREE || isCurrentLeader) {
                            viewModel.goToPage(page + 1)
                        }
                    }
                    ,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 참가자 목록 바텀시트
            if (showParticipantsSheet) {
// PdfReadScreen.kt (호출부)
                ParticipantsBottomSheet(
                    currentUserId = userId,
                    isCurrentLeader = isCurrentLeader,
                    participants = participants,
                    selectedFilterUserIds = selectedFilters,
                    currentPage = currentPage,                 // ← 추가
                    onDismiss = { showParticipantsSheet = false },
                    onTransferClick = { targetId ->
                        if (targetId != userId && isCurrentLeader) {
                            pendingTransferUserId = targetId
                        } else if (!isCurrentLeader) {
                            android.widget.Toast
                                .makeText(context, "리더만 권한을 이양할 수 있어요.", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    onToggleFilter = { id -> viewModel.toggleHighlightFilterUser(id) },
                    onClearFilter = { viewModel.clearHighlightFilter() },
                    readingMode = readingMode,
                    onChangeMode = { mode -> viewModel.setReadingMode(mode) },
                    userColors = userColors,
                    avatarKeys = avatarKeys
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

// 중앙 Dialog로 간단 팝업
            if (showHighlightPopup && selectedHighlights.isNotEmpty()) {
                HighlightPopupDialogMini(
                    highlights = selectedHighlights,      // distinctBy(id)된 리스트(이미 ViewModel에서 처리)
                    currentUserId = userId,
                    onDelete = { id ->
                        viewModel.deleteHighlight(id)
                        viewModel.hideHighlightPopup()
                    },
                    onDismiss = { viewModel.hideHighlightPopup()
                        pdfView?.dismissSelection()}
                )
            }

            if (showChatSheet && groupId != null) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showChatSheet = false },
                    sheetState = sheetState
                ) {
                    // 시트 높이를 정확히 "화면 절반"으로
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)  // 반 높이
                    ) {
                        // ChatRoomScreen 안에 자체 TopBar가 있으니 그대로 써도 되고,
                        // 필요하면 'embedded' 플래그를 만들어 상단바를 숨겨도 됨.
                        ChatRoomScreen(
                            navController = navController,
                            groupId = groupId,
                            userId = userId.toLongOrNull() ?: -1L,
                            embedded = true
                        )
                    }
                }
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
    currentPage: Int,
    onDismiss: () -> Unit,
    onTransferClick: (String) -> Unit,
    onToggleFilter: (String) -> Unit,                   // <- 여러명 토글
    onClearFilter: () -> Unit,                           // <- 전체 해제
    readingMode: PdfViewModel.ReadingMode,
    onChangeMode: (PdfViewModel.ReadingMode) -> Unit,
    userColors: Map<String, String>,
    avatarKeys: Map<String, String?>

) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 정렬: 나 → 리더(내가 아니면) → 이름/아이디 순
    val sorted = remember(participants, currentUserId, readingMode) {
        val base = if (readingMode == PdfViewModel.ReadingMode.FOLLOW) {
            participants.filter { it.userId == currentUserId || it.isOnline } // 나는 항상 보이기
        } else {
            participants
        }

        base.sortedWith(
            compareByDescending<Participant> { it.userId == currentUserId }
                .thenByDescending { it.isCurrentHost }
                .thenBy { p -> p.userName.ifBlank { "사용자${p.userId}" } }
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                ModeSwitch(
                    value = readingMode,
                    onChange = onChangeMode,     // 방장만 바뀌도록 외부에서 enabled=false 처리
                    enabled = isCurrentLeader
                )
            }

            Spacer(Modifier.height(12.dp))


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
                text = "참여자 (${sorted.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(Modifier.height(4.dp))


            sorted.forEach { p ->
                val read = p.maxReadPage >= currentPage   // ← 현재 페이지 기준 읽음/안읽음
                val colorHex = userColors[p.userId]
                val key = avatarKeys[p.userId]
                ParticipantRow(
                    me = (p.userId == currentUserId),
                    isLeader = p.isCurrentHost,
                    online = p.isOnline,                                 // ← 추가
                    readingMode = readingMode,                           // ← 추가
                    read = read,                                         // ← 추가 (오른쪽 라벨)
                    userName = p.userName.ifBlank { p.userId },
                    onClick = { if (p.userId != currentUserId) onTransferClick(p.userId) },
                    enabled = isCurrentLeader && p.userId != currentUserId,
                    avatarColorHex = colorHex,
                    avatarKey = key
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
    val bg = if (selected) Color(0xFFE0ECFF) else Color(0xFFF1F5F9)
    val fg = if (selected) BaseColor else Color(0xFF334155)
    Box(
        modifier = Modifier
            .height(32.dp) // 칩 높이 통일
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}



@Composable
private fun ParticipantRow(
    me: Boolean,
    isLeader: Boolean,
    online: Boolean,
    readingMode: PdfViewModel.ReadingMode,
    read: Boolean,
    userName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    avatarColorHex: String?,
    avatarKey: String?
) {
    val rowAlpha = if (readingMode == PdfViewModel.ReadingMode.FREE && !online) 0.5f else 1f
    val nameWeight = when {
        me -> FontWeight.Bold
        readingMode == PdfViewModel.ReadingMode.FREE && online -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val borderWidth = if (isLeader) 2.dp else 0.dp
    val borderColor = if (isLeader) BaseColor else Color.Transparent
    val avatarBg = avatarColorHex.toComposeColorOrDefault(Color(0xFFEFEFEF))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp) // ★ 고정 높이로 깔끔하게
            .alpha(rowAlpha)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AvatarBubble(
            name = userName,
            isLeader = isLeader,
            bgColor = avatarBg,
            avatarKey = avatarKey,      // 키 있으면 이미지, 없으면 이니셜
            size = 40.dp
        )


        Spacer(Modifier.width(12.dp))

        // 가운데: 이름 + 뱃지들 (한 줄, 말줄임, 오른쪽 배지에 밀리지 않게 weight 부여)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = userName,
                fontWeight = nameWeight,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = true)
            )
            if (me) {
                Spacer(Modifier.width(6.dp)); AssistChip(text = "나")
            }
            if (readingMode == PdfViewModel.ReadingMode.FREE) {
                Spacer(Modifier.width(6.dp)); AssistChip(text = if (online) "온라인" else "오프라인")
            }
        }

        // 오른쪽 읽음/안읽음
        val readColor = if (read) BaseColor else Color.Gray
        val readText  = if (read) "읽음" else "안읽음"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, readColor, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(readText, fontSize = 12.sp, color = readColor, fontWeight = FontWeight.Medium)
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

// PdfReadScreen.kt ➏ 헬퍼 함수 (파일 하단 등 공통 위치에 추가)
private fun String?.toComposeColorOrDefault(default: Color): Color {
    return try {
        this?.let { Color(android.graphics.Color.parseColor(it)) } ?: default
    } catch (_: Exception) {
        default
    }
}

@Composable
fun ModeSwitch(
    value: PdfViewModel.ReadingMode,
    onChange: (PdfViewModel.ReadingMode) -> Unit,
    enabled: Boolean
) {
    val checked = value == PdfViewModel.ReadingMode.FOLLOW

    val trackOn = Color(0xFF22C55E)   // green
    val trackOff = Color(0xFFCBD5E1)  // gray
    val trackDisabled = Color(0xFFE5E7EB)
    val thumb = Color.White

    val width = 56.dp
    val height = 32.dp
    val padding = 4.dp
    val thumbSize = 24.dp
    val maxOffset = width - padding * 2 - thumbSize

    val offset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = tween(durationMillis = 160)
    )
    val trackColor = when {
        !enabled -> trackDisabled
        checked -> trackOn
        else -> trackOff
    }

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                indication = rememberRipple(bounded = false, radius = 24.dp),
                interactionSource = remember { MutableInteractionSource() }
            ) {
                val newChecked = !checked
                onChange(if (newChecked) PdfViewModel.ReadingMode.FOLLOW
                else PdfViewModel.ReadingMode.FREE)
            }
            .padding(padding)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = if (checked) "FOLLOW" else "FREE"
                if (!enabled) disabled()
            }
        ,
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumb)
                .border(1.dp, Color(0x14000000), CircleShape)
        )
    }
}

@Composable
private fun HighlightPopupDialogMini(
    highlights: List<HighlightModel>,
    currentUserId: String,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true // 기본 너비 → 작은 팝업
        )
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("하이라이트", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    // 개수 배지(정확한 카운트)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(BaseColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("${highlights.size}", fontSize = 11.sp, color = BaseColor) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                Spacer(Modifier.height(6.dp))

                // 색 점 + (내 것이면) 삭제 아이콘만
                highlights.forEach { hl ->
                    val isMine = hl.userId == currentUserId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(start = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(hl.color.toComposeColorOrDefault(Color(0xFFE5E7EB)))
                        )
                        Spacer(Modifier.weight(1f))
                        if (isMine) {
                            IconButton(onClick = { onDelete(hl.id) }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                    contentDescription = "삭제"
                                )
                            }
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentPopupDialogMini(
    comments: List<CommentModel>,
    currentUserId: String,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember(comments) { comments.distinctBy { it.id } } // ★ 정확한 카운트

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true
        )
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("댓글", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    // ★ 칩 스타일 배지
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(BaseColor.copy(alpha = 0.12f))
                            .border(1.dp, BaseColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("${items.size}", fontSize = 11.sp, color = BaseColor, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }

                Spacer(Modifier.height(6.dp))

                items.forEachIndexed { idx, c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ★ 말풍선 느낌 박스
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                c.text,
                                fontSize = 13.sp,
                                color = Color(0xFF334155),
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        if (c.userId == currentUserId) {
                            IconButton(onClick = { onDelete(c.id) }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                    contentDescription = "삭제",
                                )
                            }
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (idx != items.lastIndex) {
                        Spacer(Modifier.height(2.dp))
                        Divider(color = Color(0xFFE2E8F0))
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarBubble(
    name: String,
    isLeader: Boolean,
    bgColor: Color,
    avatarKey: String?,
    size: Dp
) {
    // 바깥 래퍼는 clip 하지 않아 왕관이 밖으로 나갈 수 있게
    Box(modifier = Modifier.size(size + 12.dp)) { // 여유 공간 조금
        // 원형 아바타
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(bgColor)
                .then(
                    if (isLeader) Modifier.border(2.dp, Color(0xFFFFC107), CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val resId = keyToResId(avatarKey)
            if (resId != null) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.70f)
                )
            } else {
                val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp
                )
            }
        }

        // 왕관을 원 '밖'으로
        if (isLeader) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-22).dp, y = 5.dp) // 바깥으로 살짝
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .size(size * 0.5f),
                contentAlignment = Alignment.Center
            ) {
                Text("👑", fontSize = (size.value * 0.3f).sp)
            }
        }
    }
}

