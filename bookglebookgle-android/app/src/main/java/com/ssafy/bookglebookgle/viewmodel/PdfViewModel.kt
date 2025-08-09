package com.ssafy.bookglebookgle.viewmodel

import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.state.ResponseState
import com.ssafy.bookglebookgle.pdf.tools.OperationsStateHandler
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.repository.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Observer
import com.example.bookglebookgleserver.pdf.grpc.ActionType
import com.example.bookglebookgleserver.pdf.grpc.AnnotationPayload
import com.example.bookglebookgleserver.pdf.grpc.AnnotationType
import com.example.bookglebookgleserver.pdf.grpc.ReadingMode as RpcReadingMode
// UI 좌표
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates as UiCoordinates
// gRPC 좌표
import com.example.bookglebookgleserver.pdf.grpc.Coordinates as GrpcCoordinates
import com.ssafy.bookglebookgle.entity.CommentSync
import com.ssafy.bookglebookgle.entity.HighlightSync
import com.ssafy.bookglebookgle.entity.Participant
import com.ssafy.bookglebookgle.entity.PdfPageSync
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.PdfAnnotationModel
import com.ssafy.bookglebookgle.repository.PdfGrpcRepository
import com.ssafy.bookglebookgle.repository.PdfSyncConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlin.math.max


private const val TAG = "싸피_PdfViewModel"

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val pdfGrpcRepository: PdfGrpcRepository
): ViewModel(){

    private var userId: String? = null
    var currentGroupId: Long? = null
        private set

    // ========== PDF 파일 관련 상태 ==========

    private val _isPdfRenderingComplete = MutableStateFlow(false)
    val isPdfRenderingComplete: StateFlow<Boolean> = _isPdfRenderingComplete.asStateFlow()

    private val _pdfRenderingError = MutableStateFlow<String?>(null)
    val pdfRenderingError: StateFlow<String?> = _pdfRenderingError.asStateFlow()

    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile.asStateFlow()

    private val _pdfTitle = MutableStateFlow("")
    val pdfTitle: StateFlow<String> = _pdfTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _pdfLoadError = MutableStateFlow<String?>(null)
    val pdfLoadError: StateFlow<String?> = _pdfLoadError.asStateFlow()

    private val _pdfReady = MutableStateFlow(false)
    val pdfReady: StateFlow<Boolean> = _pdfReady.asStateFlow()

    // ========== PDF 뷰어 상태 ==========

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _showPageInfo = MutableStateFlow(false)
    val showPageInfo: StateFlow<Boolean> = _showPageInfo.asStateFlow()

    // ========== 텍스트 선택 및 주석 상태 ==========

    private val _showTextSelectionOptions = MutableStateFlow(false)
    val showTextSelectionOptions: StateFlow<Boolean> = _showTextSelectionOptions.asStateFlow()

    private val _textSelectionPoint = MutableStateFlow(PointF(0f, 0f))
    val textSelectionPoint: StateFlow<PointF> = _textSelectionPoint.asStateFlow()

    private val _selectedComments = MutableStateFlow<List<CommentModel>>(emptyList())
    val selectedComments: StateFlow<List<CommentModel>> = _selectedComments.asStateFlow()

    private val _commentPopupPoint = MutableStateFlow(PointF(0f, 0f))
    val commentPopupPoint: StateFlow<PointF> = _commentPopupPoint.asStateFlow()

    private val _showCommentPopup = MutableStateFlow(false)
    val showCommentPopup: StateFlow<Boolean> = _showCommentPopup.asStateFlow()

    // ========== 주석 데이터 ==========

    private val _annotations = MutableStateFlow(AnnotationListResponse())
    val annotations: StateFlow<AnnotationListResponse> = _annotations.asStateFlow()

    // ========== 상태 핸들러들 ==========

    val addCommentResponse = OperationsStateHandler(viewModelScope)
    val updateCommentResponse = OperationsStateHandler(viewModelScope)
    val deleteCommentResponse = OperationsStateHandler(viewModelScope)

    val addHighlightResponse = OperationsStateHandler(viewModelScope)
    val deleteHighlightResponse = OperationsStateHandler(viewModelScope)

    val annotationListResponse = OperationsStateHandler(viewModelScope)

    // Thumbnails
    private val _thumbnails = MutableStateFlow<List<Bitmap>>(emptyList())
    val thumbnails: StateFlow<List<Bitmap>> = _thumbnails.asStateFlow()

    // gRPC
    private val _syncConnected = MutableStateFlow(false)
    val syncConnected: StateFlow<Boolean> = _syncConnected.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()


    private val highlightAddObserver = Observer<HighlightSync> { sync -> handleRemoteHighlightAdd(sync) }
    private val highlightDeleteObserver = Observer<Long> { id -> handleRemoteHighlightDelete(id) }

    private val commentAddObserver = Observer<CommentSync> { sync -> handleRemoteCommentAdd(sync) }
    private val commentUpdateObserver = Observer<CommentSync> { sync -> handleRemoteCommentUpdate(sync) }
    private val commentDeleteObserver = Observer<Long> { id -> handleRemoteCommentDelete(id) }

    private val _highlightFilterUserIds = MutableStateFlow<Set<String>>(emptySet())
    val highlightFilterUserIds: StateFlow<Set<String>> = _highlightFilterUserIds.asStateFlow()

    private val _selectedHighlights = MutableStateFlow<List<HighlightModel>>(emptyList())
    val selectedHighlights: StateFlow<List<HighlightModel>> = _selectedHighlights.asStateFlow()

    private val _highlightPopupPoint = MutableStateFlow(PointF(0f, 0f))
    val highlightPopupPoint: StateFlow<PointF> = _highlightPopupPoint.asStateFlow()

    private val _showHighlightPopup = MutableStateFlow(false)
    val showHighlightPopup: StateFlow<Boolean> = _showHighlightPopup.asStateFlow()


    // ↑ 클래스 필드에 추가
    private data class PendingHighlightKey(val page: Int, val snippet: String, val color: String,
                                           val x1: Double, val y1: Double, val x2: Double, val y2: Double)
    private data class PendingCommentKey(val page: Int, val snippet: String, val text: String,
                                         val x1: Double, val y1: Double, val x2: Double, val y2: Double)

    private val pendingHighlights = mutableMapOf<PendingHighlightKey, Long>() // key -> tempId
    private val pendingComments   = mutableMapOf<PendingCommentKey, Long>()

    private var appInForeground = true
    private var autoTransferJob: Job? = null
    private val AUTO_TRANSFER_DELAY = 10_000L // 10초 정도

    private var progressDebounceJob: Job? = null
    private var lastProgressPage: Int = 0

    enum class ReadingMode { FOLLOW, FREE }


    private val _readingMode = MutableStateFlow(ReadingMode.FREE)
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    private val _myMaxReadPage = MutableStateFlow(0)
    val myMaxReadPage: StateFlow<Int> = _myMaxReadPage.asStateFlow()

    private val _progressByUser = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progressByUser: StateFlow<Map<String, Int>> = _progressByUser.asStateFlow()

    private val _colorByUser = MutableStateFlow<Map<String, String>>(emptyMap())
    val colorByUser: StateFlow<Map<String, String>> = _colorByUser.asStateFlow()

    // 온라인 상태 (맵 또는 집합)
    private val _onlineByUser = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val onlineByUser: StateFlow<Map<String, Boolean>> = _onlineByUser.asStateFlow()


    fun isRead(userId: String, page1Based: Int): Boolean =
        (progressByUser.value[userId] ?: 0) >= page1Based

    fun showCommentPopup(comments: List<CommentModel>, point: PointF) {
        _selectedComments.value = comments.distinctBy { it.id }
        _commentPopupPoint.value = point
        _showCommentPopup.value = true
    }

    fun showHighlightPopup(highlights: List<HighlightModel>, point: PointF) {
        _selectedHighlights.value = highlights.distinctBy { it.id }
        _highlightPopupPoint.value = point
        _showHighlightPopup.value = true
    }


    fun hideHighlightPopup() {
        _showHighlightPopup.value = false
        _selectedHighlights.value = emptyList()
        _highlightPopupPoint.value = PointF(0f, 0f)
    }


    fun initFromGroupDetail(
        pageCount: Int,
        initialMembers: List<GroupDetailViewModel.InitialMember>,
        initialProgress: Map<String, Int>
    ) {
        _totalPages.value = pageCount

        _participants.value = initialMembers.map {
            Participant(
                userId = it.userId,
                userName = it.nickname,
                isOriginalHost = it.isHost,
                isCurrentHost = false
            )
        }

        _progressByUser.value = initialProgress
        _myMaxReadPage.value = initialProgress[myUserId] ?: _myMaxReadPage.value

        _colorByUser.value = initialMembers.associate { m ->
            m.userId to (m.color?.takeIf { it.isNotBlank() } ?: "#E5E7EB")
        }
    }



    fun setReadingMode(mode: ReadingMode) {
        if (!_isCurrentLeader.value) return

        val gid = currentGroupId ?: return
        val uid = myUserId
        if (syncConnected.value) {
            pdfGrpcRepository.sendReadingMode(gid, uid, mode.toGrpc()) // 여기!
        }
        // 선택: 로컬 프리뷰
        _readingMode.value = mode
        if (mode == ReadingMode.FOLLOW && _isCurrentLeader.value) {
            pdfGrpcRepository.sendPageUpdate(_currentPage.value)
        }
    }


    private fun scheduleProgressUpdate(newPage: Int) {
        // 페이지가 늘어난 경우만 반영
        if (newPage <= _myMaxReadPage.value) return
        _myMaxReadPage.value = newPage
        lastProgressPage = newPage
        progressDebounceJob?.cancel()
        progressDebounceJob = viewModelScope.launch {
            delay(400) // 스크롤 중 난사 방지
            val gid = currentGroupId ?: return@launch
            val uid = userId ?: return@launch
            if (_syncConnected.value) {
                pdfGrpcRepository.sendProgressUpdate(gid, uid, lastProgressPage)
            }
        }
    }


    fun toggleHighlightFilterUser(userId: String) {
        _highlightFilterUserIds.update { cur ->
            if (userId in cur) cur - userId else cur + userId
        }
        applyAnnotationFilter()
    }
    fun clearHighlightFilter() {
        _highlightFilterUserIds.value = emptySet()
        applyAnnotationFilter()
    }


    // 필터 적용 로직 수정
    private fun applyAnnotationFilter() {
        val selected = _highlightFilterUserIds.value
        val ann = _annotations.value

        val filteredHighlights =
            if (selected.isEmpty()) ann.highlights
            else ann.highlights.filter { h -> h.userId != null && h.userId in selected }

        val filteredComments =
            if (selected.isEmpty()) ann.comments
            else ann.comments.filter { c -> c.userId != null && c.userId in selected }

        val models = buildList {
            addAll(filteredComments.map { it.updateAnnotationData() })
            addAll(filteredHighlights.map { it.updateAnnotationData() })
        }
        currentPdfView?.loadAnnotations(emptyList())
        currentPdfView?.loadAnnotations(models)
    }



    /**
     * PDF 렌더링 시작 알림
     */
    fun onPdfRenderingStarted() {
        Log.d(TAG, "==== PDF 렌더링 시작 알림 ====")
        _isPdfRenderingComplete.value = false
        _pdfRenderingError.value = null
    }

    /**
     * PDF 렌더링 성공 알림
     */
    fun onPdfRenderingSuccess() {
        Log.d(TAG, "==== PDF 렌더링 성공 알림 ====")
        _isPdfRenderingComplete.value = true
        _pdfRenderingError.value = null

        generateThumbnails()
    }

    /**
     * PDF 렌더링 실패 알림
     */
    fun onPdfRenderingFailed(error: String) {
        Log.e(TAG, "==== PDF 렌더링 실패 알림 ====")
        Log.e(TAG, "에러: $error")
        _isPdfRenderingComplete.value = false
        _pdfRenderingError.value = error
    }

    /**
     * 그룹 ID 설정
     */
    fun setGroupId(groupId: Long) {
        Log.d(TAG, "==== 그룹 ID 설정 ====")
        Log.d(TAG, "이전 그룹 ID: $currentGroupId")
        Log.d(TAG, "새 그룹 ID: $groupId")
        currentGroupId = groupId
    }

    /**
     * 그룹 PDF 파일을 서버에서 다운로드
     */
    fun loadGroupPdf(groupId: Long, context: android.content.Context) {
        Log.d(TAG, "==== PDF 다운로드 시작 ====")
        Log.d(TAG, "그룹 ID: $groupId")
        Log.d(TAG, "현재 로딩 상태: ${_isLoading.value}")

        _isLoading.value = true
        _pdfLoadError.value = null
        _pdfReady.value = false

        viewModelScope.launch {
            try {
                Log.d(TAG, "Repository에서 PDF 다운로드 요청 시작")
                val pdfData = pdfRepository.getGroupPdf(groupId)

                if (pdfData != null && pdfData.inputStream != null) {
                    Log.d(TAG, "PDF 다운로드 성공")
                    Log.d(TAG, "파일명: ${pdfData.fileName}")

                    // PDF 제목 설정 (확장자 제거)
                    val title = pdfData.fileName.removeSuffix(".pdf")
                    _pdfTitle.value = title
                    Log.d(TAG, "PDF 제목 설정: $title")

                    // PDF 바이트 데이터 읽기
                    Log.d(TAG, "PDF 바이트 데이터 읽기 시작")
                    val bytes = pdfData.inputStream.readBytes()
                    Log.d(TAG, "PDF 크기: ${bytes.size} bytes")

                    // PDF 헤더 검증
                    if (bytes.size >= 4 && String(bytes.sliceArray(0..3)) == "%PDF") {
                        Log.d(TAG, "유효한 PDF 파일 확인됨")

                        // 임시 파일 생성
                        val tempFile = File.createTempFile("group_pdf_$groupId", ".pdf", context.cacheDir)
                        tempFile.writeBytes(bytes)

                        Log.d(TAG, "임시 파일 저장 완료: ${tempFile.absolutePath}")
                        Log.d(TAG, "임시 파일 크기: ${tempFile.length()} bytes")

                        _pdfFile.value = tempFile
                        _pdfReady.value = true
                        _isLoading.value = false

                        Log.d(TAG, "PDF 로드 완료 - 상태 업데이트 완료")
                    } else {
                        val error = "유효하지 않은 PDF 파일 - 헤더: ${if (bytes.size >= 4) String(bytes.sliceArray(0..3)) else "크기 부족"}"
                        Log.e(TAG, error)
                        _pdfLoadError.value = error
                        _isLoading.value = false
                        _pdfReady.value = false
                    }
                } else {
                    val error = "PDF 파일을 다운로드할 수 없습니다 - pdfData: $pdfData"
                    Log.e(TAG, error)
                    _pdfLoadError.value = error
                    _isLoading.value = false
                    _pdfReady.value = false
                }
            } catch (e: Exception) {
                val error = "PDF 다운로드 중 오류가 발생했습니다: ${e.message}"
                Log.e(TAG, error, e)
                _pdfLoadError.value = error
                _isLoading.value = false
                _pdfReady.value = false
            }
        }
    }

    // ========== PDF 뷰어 상태 관리 ==========

    fun updateCurrentPage(page: Int) {
        Log.d(TAG, "==== 페이지 변경 ====")
        Log.d(TAG, "이전 페이지: ${_currentPage.value}")
        Log.d(TAG, "새 페이지: $page")
        _currentPage.value = page
        _showPageInfo.value = true
    }

    fun updateTotalPages(pages: Int) {
        Log.d(TAG, "==== 총 페이지 수 설정 ====")
        Log.d(TAG, "총 페이지: $pages")
        _totalPages.value = pages
    }

    fun hidePageInfo() {
        Log.d(TAG, "페이지 정보 숨김")
        _showPageInfo.value = false
    }

    // ========== 텍스트 선택 관리 ==========

    fun showTextSelection(point: PointF) {
        Log.d(TAG, "==== 텍스트 선택 표시 ====")
        Log.d(TAG, "위치: (${point.x}, ${point.y})")
        _textSelectionPoint.value = point
        _showTextSelectionOptions.value = true
    }

    fun hideTextSelection() {
        Log.d(TAG, "텍스트 선택 숨김")
        _showTextSelectionOptions.value = false
        _textSelectionPoint.value = PointF(0f, 0f)
    }

    // ========== 댓글 팝업 관리 ==========

    fun hideCommentPopup() {
        Log.d(TAG, "댓글 팝업 숨김")
        _showCommentPopup.value = false
        _selectedComments.value = emptyList()
        _commentPopupPoint.value = PointF(0f, 0f)
    }

    // ========== 댓글 관리 ==========

    /**
     * 댓글 추가
     */
    fun addComment(snippet: String, text: String, page: Int, coordinates: UiCoordinates) {
        val gid = currentGroupId
        Log.d(TAG, "[addComment] 요청 시작  snippet=$snippet, text=$text, page=$page, coords=$coordinates, groupId=$gid")
        if (gid == null) {
            Log.e(TAG, "[addComment] 실패: groupId가 설정되지 않음")
            return
        }

        // 1) 로컬 즉시 반영
        val tempId = System.currentTimeMillis()
        val model = CommentModel(
            id = tempId,
            page = page,
            snippet = snippet,
            text = text,
            coordinates = coordinates,
            userId = myUserId
        )

        val filter = _highlightFilterUserIds.value
        if (filter.isEmpty() || myUserId in filter) {
            currentPdfView?.addComment(model) // 또는 addComment
        }
        _annotations.update { it.copy(comments = ArrayList(it.comments + model)) }

        val key = PendingCommentKey(page, snippet, text,
            coordinates.startX, coordinates.startY, coordinates.endX, coordinates.endY)
        pendingComments[key] = tempId

        // 2) gRPC 메시지 전송
        // 2) gRPC 좌표 메시지 변환
        val grpcCoords = GrpcCoordinates.newBuilder()
            .setStartX(coordinates.startX)
            .setStartY(coordinates.startY)
            .setEndX(coordinates.endX)
            .setEndY(coordinates.endY)
            .build()
        Log.d(TAG, "[addComment] grpcCoords=$grpcCoords")

        // 3) AnnotationPayload 생성
        val payload = AnnotationPayload.newBuilder()
            .setPage(page)
            .setSnippet(snippet)
            .setText(text)
            .setCoordinates(grpcCoords)   // positional argument
            .build()
        Log.d(TAG, "[addComment] payload=$payload")


        Log.d(TAG, "[addComment] gRPC 전송 payload=$payload")
        pdfGrpcRepository.sendAnnotation(
            gid,
            userId ?: return,
            AnnotationType.COMMENT,
            payload,
            ActionType.ADD
        )
        Log.d(TAG, "[addComment] gRPC 전송 완료")
    }

    fun updateComment(commentId: Long, newText: String) {
        val gid = currentGroupId
        Log.d(TAG, "[updateComment] 요청 시작  commentId=$commentId, newText=$newText, groupId=$gid")
        if (gid == null) {
            Log.e(TAG, "[updateComment] 실패: groupId가 설정되지 않음")
            return
        }

        // 1) 로컬 즉시 반영
        val original = _annotations.value.comments.find { it.id == commentId }
        if (original == null) {
            Log.e(TAG, "[updateComment] 실패: 로컬에 commentId=$commentId 없음")
            return
        }
        val updated = original.copy(text = newText)
        Log.d(TAG, "[updateComment] 로컬 반영 전: ${original.text}")
        currentPdfView?.updateComment(updated)
        _annotations.update {
            it.copy(comments = ArrayList(it.comments.map { c -> if (c.id == commentId) updated else c }))
        }
        Log.d(TAG, "[updateComment] 로컬 반영 후: ${updated.text}")

        // 2) gRPC 메시지 전송
        val payload = AnnotationPayload.newBuilder()
            .setId(commentId)
            .setText(newText)
            .build()

        Log.d(TAG, "[updateComment] gRPC 전송 payload=$payload")
        pdfGrpcRepository.sendAnnotation(
            gid,
            userId ?: return,
            AnnotationType.COMMENT,
            payload,
            ActionType.UPDATE
        )
        Log.d(TAG, "[updateComment] gRPC 전송 완료")
    }

    fun deleteComment(commentId: Long) {
        val gid = currentGroupId
        Log.d(TAG, "[deleteComment] 요청 시작  commentId=$commentId, groupId=$gid")
        if (gid == null) {
            Log.e(TAG, "[deleteComment] 실패: groupId가 설정되지 않음")
            return
        }

        // 1) 로컬 즉시 반영
        Log.d(TAG, "[deleteComment] 로컬 반영 전 annotations.size=${_annotations.value.comments.size}")
        currentPdfView?.removeCommentAnnotations(listOf(commentId))
        _annotations.update {
            it.copy(comments = ArrayList(it.comments.filterNot { it.id == commentId }))
        }
        Log.d(TAG, "[deleteComment] 로컬 반영 후 annotations.size=${_annotations.value.comments.size}")

        // 2) gRPC 메시지 전송
        val payload = AnnotationPayload.newBuilder()
            .setId(commentId)
            .build()

        Log.d(TAG, "[deleteComment] gRPC 전송 payload=$payload")
        pdfGrpcRepository.sendAnnotation(
            gid,
            userId ?: return,
            AnnotationType.COMMENT,
            payload,
            ActionType.DELETE
        )
        Log.d(TAG, "[deleteComment] gRPC 전송 완료")
    }

    // ========== 하이라이트 관리 ==========

    /**
     * 하이라이트 추가
     */
    fun addHighlight(snippet: String, color: String, page: Int, coordinates: UiCoordinates) {
        val gid = currentGroupId
        Log.d(TAG, "[addHighlight] 요청 시작  snippet=$snippet, color=$color, page=$page, coords=$coordinates, groupId=$gid")
        if (gid == null) {
            Log.e(TAG, "[addHighlight] 실패: groupId가 설정되지 않음")
            return
        }

        // 1) 로컬 즉시 반영
        val tempId = System.currentTimeMillis()
        val model = HighlightModel(
            id = tempId,
            page = page,
            snippet = snippet,
            color = color,
            coordinates = coordinates,
            userId = myUserId
        )


        val filter = _highlightFilterUserIds.value
        if (filter.isEmpty() || myUserId in filter) {
            currentPdfView?.addHighlight(model) // 또는 addComment
        }
        _annotations.update { it.copy(highlights = ArrayList(it.highlights + model)) }


        val key = PendingHighlightKey(page, snippet, color,
            coordinates.startX, coordinates.startY, coordinates.endX, coordinates.endY)
        pendingHighlights[key] = tempId

        // 2) gRPC 메시지 전송
        // 2) gRPC 좌표 메시지 변환
        val grpcCoords = GrpcCoordinates.newBuilder()
            .setStartX(coordinates.startX)
            .setStartY(coordinates.startY)
            .setEndX(coordinates.endX)
            .setEndY(coordinates.endY)
            .build()
        Log.d(TAG, "[addHighlight] grpcCoords=$grpcCoords")

        // 3) AnnotationPayload 생성
        val payload = AnnotationPayload.newBuilder()
            .setPage(page)
            .setSnippet(snippet)
            .setColor(color)
            .setCoordinates(grpcCoords)   // positional argument
            .build()
        Log.d(TAG, "[addHighlight] payload=$payload")

        Log.d(TAG, "[addHighlight] gRPC 전송 payload=$payload")
        pdfGrpcRepository.sendAnnotation(
            gid,
            userId ?: return,
            AnnotationType.HIGHLIGHT,
            payload,
            ActionType.ADD
        )
        Log.d(TAG, "[addHighlight] gRPC 전송 완료")
    }

    fun deleteHighlight(highlightId: Long) {
        val gid = currentGroupId
        Log.d(TAG, "[deleteHighlight] 요청 시작  highlightId=$highlightId, groupId=$gid")
        if (gid == null) {
            Log.e(TAG, "[deleteHighlight] 실패: groupId가 설정되지 않음")
            return
        }

        // 1) 로컬 즉시 반영
        Log.d(TAG, "[deleteHighlight] 로컬 반영 전 highlights.size=${_annotations.value.highlights.size}")
        currentPdfView?.removeHighlightAnnotations(listOf(highlightId))
        _annotations.update {
            it.copy(highlights = ArrayList(it.highlights.filterNot { it.id == highlightId }))
        }
        Log.d(TAG, "[deleteHighlight] 로컬 반영 후 highlights.size=${_annotations.value.highlights.size}")

        // 2) gRPC 메시지 전송
        val payload = AnnotationPayload.newBuilder().setId(highlightId).build()

        Log.d(TAG, "[deleteHighlight] gRPC 전송 payload=$payload")
        pdfGrpcRepository.sendAnnotation(
            gid,
            userId ?: return,
            AnnotationType.HIGHLIGHT,
            payload,
            ActionType.DELETE
        )
        Log.d(TAG, "[deleteHighlight] gRPC 전송 완료")
    }

    // ========== 전체 주석 관리 ==========

    /**
     * 모든 주석 로드
     */
    fun loadAllAnnotations() {
        val groupId = currentGroupId
        Log.d(TAG, "==== 모든 주석 로드 요청 ====")
        Log.d(TAG, "그룹 ID: $groupId")

        if (groupId == null) {
            Log.e(TAG, "주석 로드 실패: 그룹 ID가 설정되지 않음")
            return
        }

        annotationListResponse.load {
            try {
                Log.d(TAG, "Repository에서 전체 주석 조회 요청 시작")
                val result = pdfRepository.getAllAnnotations(groupId)


                if (result != null) {
                    Log.d(TAG, "주석 로드 성공")
                    Log.d(TAG, "댓글 수: ${result.comments.size}")
                    Log.d(TAG, "하이라이트 수: ${result.highlights.size}")


                    // 각 주석 상세 로그
                    result.comments.forEachIndexed { index, comment ->
                        Log.d(TAG, "댓글 $index: ID=${comment.id}, 페이지=${comment.page}, 텍스트=${comment.text}")
                    }
                    result.highlights.forEachIndexed { index, highlight ->
                        Log.d(TAG, "하이라이트 $index: ID=${highlight.id}, 페이지=${highlight.page}, 색상=${highlight.color}")
                    }


                    _annotations.value = result
                    Log.d(TAG, "로컬 상태 업데이트 완료")

                    applyAnnotationFilter()

                    ResponseState.Success(result)
                } else {
                    Log.e(TAG, "주석 로드 실패: 서버에서 null 응답")
                    throw Exception("주석 목록을 불러올 수 없습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "주석 로드 예외 발생", e)
                throw e
            }
        }
    }

    // ========== 상태 초기화 ==========

    fun resetPdfState() {
        Log.d(TAG, "==== PDF 상태 초기화 ====")
        Log.d(TAG, "이전 상태 - 파일: ${_pdfFile.value?.name}, 제목: ${_pdfTitle.value}")

        _pdfFile.value = null
        _pdfTitle.value = ""
        _isLoading.value = false
        _pdfLoadError.value = null
        _pdfReady.value = false

        // 렌더링 상태도 초기화
        _isPdfRenderingComplete.value = false
        _pdfRenderingError.value = null

        _currentPage.value = 1
        _totalPages.value = 1
        _showPageInfo.value = false
        _annotations.value = AnnotationListResponse()
        hideTextSelection()
        hideCommentPopup()

        Log.d(TAG, "PDF 상태 초기화 완료")
    }

    // ========== 로컬 상태 업데이트 헬퍼 메서드 ==========

    private fun removeCommentsFromLocal(deletedIds: List<Long>) {
        Log.d(TAG, "==== 로컬에서 댓글 제거 ====")
        Log.d(TAG, "삭제할 댓글 ID들: $deletedIds")

        val currentAnnotations = _annotations.value
        val beforeCount = currentAnnotations.comments.size
        Log.d(TAG, "삭제 전 댓글 수: $beforeCount")

        val updatedComments = currentAnnotations.comments.filter { comment ->
            val shouldKeep = !deletedIds.contains(comment.id)
            if (!shouldKeep) {
                Log.d(TAG, "댓글 제거: ID=${comment.id}, 텍스트=${comment.text}")
            }
            shouldKeep
        }

        _annotations.value = currentAnnotations.copy(comments = ArrayList(updatedComments))
        Log.d(TAG, "댓글 제거 완료 - 이전: ${beforeCount}개, 현재: ${updatedComments.size}개")
    }

    private fun removeHighlightsFromLocal(deletedIds: List<Long>) {
        Log.d(TAG, "==== 로컬에서 하이라이트 제거 ====")
        Log.d(TAG, "삭제할 하이라이트 ID들: $deletedIds")

        val currentAnnotations = _annotations.value
        val beforeCount = currentAnnotations.highlights.size
        Log.d(TAG, "삭제 전 하이라이트 수: $beforeCount")

        val updatedHighlights = currentAnnotations.highlights.filter { highlight ->
            val shouldKeep = !deletedIds.contains(highlight.id)
            if (!shouldKeep) {
                Log.d(TAG, "하이라이트 제거: ID=${highlight.id}, 색상=${highlight.color}")
            }
            shouldKeep
        }

        _annotations.value = currentAnnotations.copy(highlights = ArrayList(updatedHighlights))
        Log.d(TAG, "하이라이트 제거 완료 - 이전: ${beforeCount}개, 현재: ${updatedHighlights.size}개")
    }


    // 기존 gRPC 섹션을 이 코드로 완전히 교체하세요

    //gRPC

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _isCurrentLeader = MutableStateFlow(false)  // 현재 진행자인지
    val isCurrentLeader: StateFlow<Boolean> = _isCurrentLeader.asStateFlow()

    private val _currentLeaderId = MutableStateFlow<String?>(null)
    val currentLeaderId: StateFlow<String?> = _currentLeaderId.asStateFlow()

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants: StateFlow<List<Participant>> = _participants.asStateFlow()

    private var myUserId: String = ""
    private var groupId: Long = 0

    fun setUserInfo(userId: String, groupId: Long, isHostFromNav: Boolean) {
        this.myUserId = userId
        this.groupId = groupId
        _isHost.value = isHostFromNav
    }

    // 4) UI 클릭 시 권한 이양
    fun transferLeadershipToUser(targetUserId: String) {
        if (_isCurrentLeader.value && targetUserId != myUserId) {
            pdfGrpcRepository.transferLeadership(groupId, myUserId, targetUserId)
        }
    }



    fun onAppForeground() {
        appInForeground = true
        autoTransferJob?.cancel()

        if (!pdfGrpcRepository.isConnected()) {
            currentGroupId?.let { gid -> userId?.let { uid ->
                pdfGrpcRepository.connectAndJoinRoom(gid, uid)
            } }
            // 연결되면 아래 동작은 connectionObserver의 CONNECTED에서 처리
            return
        }

        // 이미 연결 중이면 스냅샷(참가자/진도)부터 받아오자
        pdfGrpcRepository.sendJoinRequest()

        when (readingMode.value) {
            ReadingMode.FOLLOW -> {
                // FOLLOW + 리더일 때만 현재 페이지 브로드캐스트
                if (isCurrentLeader.value) {
                    pdfGrpcRepository.sendPageUpdate(currentPage.value)
                }
            }
            ReadingMode.FREE -> {
                // FREE는 페이지 이동 브로드캐스트 안 함 (스냅샷으로 진도 최신화됨)
                // 필요하면 내 현재 maxReadPage를 서버에 갱신 (선택)
            }
        }
    }

    fun onAppBackground() {
        appInForeground = false
        if (isCurrentLeader.value) {
            autoTransferJob?.cancel()
            autoTransferJob = viewModelScope.launch(Dispatchers.IO) {
                delay(AUTO_TRANSFER_DELAY)
                if (!appInForeground && isCurrentLeader.value) {
                    selectNextLeader()?.let { transferLeadershipToUser(it.userId) }
                }
            }
        }
    }


    // 간단한 후보 선택 로직(원하면 정교화)
    private fun selectNextLeader(): Participant? =
        participants.value.firstOrNull { it.userId != myUserId }



    // Observer들 - Chat과 동일한 패턴
    private val pdfPageObserver = Observer<PdfPageSync> { pageSync ->
        handleRemotePageChange(pageSync.page)
    }

    private val joinObserver = Observer<String> { joinerId ->
        if (_participants.value.none { it.userId == joinerId }) {
            _participants.value = _participants.value + Participant(
                userId = joinerId, userName = "",
                isOriginalHost = false, isCurrentHost = false
            )
        }
    }

    private val leadershipObserver = Observer<String> { newHostId ->
        _currentLeaderId.value = newHostId
        _isCurrentLeader.value = (newHostId == myUserId)
        _participants.value = _participants.value.map { p ->
            p.copy(isCurrentHost = (p.userId == newHostId))
        }
    }

    // ParticipantsSnapshot 타입 주의!
    // PdfViewModel.kt (기존 participantsObserver 교체)
// 타입: Observer<com.example.bookglebookgleserver.pdf.grpc.ParticipantsSnapshot>
    private val participantsObserver =
        Observer<com.example.bookglebookgleserver.pdf.grpc.ParticipantsSnapshot> { snapshot ->

            val base = _participants.value  // ← initFromGroupDetail로 넣어둔 '모임원 전체'를 기준으로 유지
            val onlineMap = snapshot.onlineByUserMap
            val progMap   = snapshot.progressByUserMap

            // 스냅샷 내 호스트/닉네임 정보를 가져오기 위한 빠른 룩업
            val snapById = snapshot.participantsList.associateBy { it.userId }
            val hostId   = snapshot.participantsList.firstOrNull { it.isCurrentHost }?.userId

            // 1) 기존 모임원 리스트를 '삭제 없이' 필드만 업데이트(merge)
            val merged = base.map { p ->
                val snap = snapById[p.userId]
                val newOnline = onlineMap[p.userId] ?: p.isOnline
                val newProg   = progMap[p.userId] ?: p.maxReadPage
                val newIsHost = if (hostId != null) p.userId == hostId else p.isCurrentHost
                p.copy(
                    userName       = if (p.userName.isBlank()) (snap?.userName ?: p.userName) else p.userName,
                    isOriginalHost = snap?.isOriginalHost ?: p.isOriginalHost,
                    isCurrentHost  = newIsHost,
                    isOnline       = newOnline,
                    maxReadPage    = max(p.maxReadPage, newProg)
                )
            }

            // 2) base에 없던(= gRPC에서만 온) 구성원이 있으면 '추가' (삭제는 절대 안 함)
            val extraFromSnap = snapshot.participantsList
                .filter { sp -> merged.none { it.userId == sp.userId } }
                .map { sp ->
                    com.ssafy.bookglebookgle.entity.Participant(
                        userId         = sp.userId,
                        userName       = sp.userName,
                        isOriginalHost = sp.isOriginalHost,
                        isCurrentHost  = sp.isCurrentHost,
                        isOnline       = onlineMap[sp.userId] == true,
                        maxReadPage    = progMap[sp.userId] ?: 0
                    )
                }

            val finalList = merged + extraFromSnap
            _participants.value = finalList

            // 3) 리더 상태 반영
            _currentLeaderId.value = hostId
            _isCurrentLeader.value = (hostId == myUserId)

            _readingMode.value = snapshot.readingMode.toLocal()

            // 4) FOLLOW 모드에서만(내가 리더가 아닐 때만) 페이지 스냅 동기화
            val page = snapshot.currentPage
            if (_readingMode.value == ReadingMode.FOLLOW &&
                !_isCurrentLeader.value &&
                page > 0 && page != _currentPage.value) {
                currentPdfView?.jumpTo(page - 1, false, false, false)
                currentPdfView?.centerCurrentPage(withAnimation = false)
                _currentPage.value = page
                _showPageInfo.value = true
            }
        }




    private var currentPdfView: com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView? = null
    private var isRemotePageChange = false

    private val connectionObserver = Observer<PdfSyncConnectionStatus> { status ->
        _syncConnected.value = status == PdfSyncConnectionStatus.CONNECTED

        when (status) {
            is PdfSyncConnectionStatus.ERROR -> {
                _syncError.value = status.message
                Log.e(TAG, "gRPC 연결 에러: ${status.message}")
            }
            is PdfSyncConnectionStatus.CONNECTING -> {
                _syncError.value = null
                Log.d(TAG, "gRPC 연결 중...")
            }
            is PdfSyncConnectionStatus.CONNECTED -> {
                _syncError.value = null
                // 재접속 직후: 스냅샷 요청
                Log.d(TAG, "gRPC 연결 완료")
                pdfGrpcRepository.sendJoinRequest()

                if (readingMode.value == ReadingMode.FOLLOW && _isCurrentLeader.value) {
                    pdfGrpcRepository.sendPageUpdate(_currentPage.value)
                }
                // FREE는 그냥 스냅샷만으로 충분 (원하면 아래 호출)
                // if (readingMode.value == ReadingMode.FREE) sendProgressUpdateIfNeeded()
            }

            is PdfSyncConnectionStatus.DISCONNECTED -> {
                _syncError.value = null
                Log.d(TAG, "gRPC 연결 해제")
            }
        }
    }

    // PdfViewModel.kt (클래스 안에 필드로 추가)
    private val progressObserver = Observer<Pair<String, Int>> { pair ->
        val uid = pair.first
        val page = pair.second

        // 1) 전체 맵 갱신 (최댓값 유지)
        _progressByUser.update { prev ->
            prev + (uid to max(prev[uid] ?: 0, page))
        }

        // 2) participants 갱신
        _participants.update { list ->
            list.map { p ->
                if (p.userId == uid) p.copy(
                    maxReadPage = max(p.maxReadPage, page),
                    isOnline = true
                ) else p
            }
        }

        // 3) 내 것이라면 내 max 갱신
        if (uid == myUserId) {
            _myMaxReadPage.value = max(_myMaxReadPage.value, page)
        }
    }


    init {
        // Observer 등록 - Chat과 동일한 패턴
        pdfGrpcRepository.newPageUpdates.observeForever(pdfPageObserver)
        pdfGrpcRepository.connectionStatus.observeForever(connectionObserver)
        // 새로 추가한 하이라이트/코멘트 옵저버 등록
        pdfGrpcRepository.newHighlights.observeForever(highlightAddObserver)
        pdfGrpcRepository.deletedHighlights.observeForever(highlightDeleteObserver)
        pdfGrpcRepository.newComments.observeForever(commentAddObserver)
        pdfGrpcRepository.updatedComments.observeForever(commentUpdateObserver)
        pdfGrpcRepository.deletedComments.observeForever(commentDeleteObserver)
        pdfGrpcRepository.joinRequests.observeForever(joinObserver)
        pdfGrpcRepository.leadershipTransfers.observeForever(leadershipObserver)
        pdfGrpcRepository.participantsSnapshot.observeForever(participantsObserver)
        pdfGrpcRepository.progressUpdates.observeForever(progressObserver)



    }


    // PDFView 참조 저장
    fun setPdfView(pdfView: com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView) {
        Log.d(TAG, "PDFView 참조 저장")
        this.currentPdfView = pdfView
    }

    /**
     * gRPC 동기화 연결 - 기존과 동일하지만 Repository 사용
     */
    fun connectToSync(groupId: Long, userId: String) {
        this.currentGroupId = groupId
        this.userId = userId

        // 이미 initFromGroupDetail()에서 전체 멤버를 넣어뒀다면 유지
        if (_participants.value.none { it.userId == myUserId }) {
            _participants.update { it + Participant(
                userId = myUserId,
                userName = "",
                isOriginalHost = _isHost.value,
                isCurrentHost = _isCurrentLeader.value
            ) }
        }

        pdfGrpcRepository.connectAndJoinRoom(groupId, userId)
    }


    fun leaveSyncRoom() {
        Log.d(TAG, "PDF 뷰어 나가기 - gRPC 방 떠나기")
        pdfGrpcRepository.leaveRoom()
    }

    fun handleRemotePageChange(page: Int) {
        // FREE 모드면 무시(각자 자유)
        if (_readingMode.value == ReadingMode.FREE) return

        Log.d(TAG, "원격 페이지 변경 수신: $page")
        // FOLLOW 모드에서만, 리더가 아닌 경우에만 적용
        if (_isCurrentLeader.value) return
        if (_currentPage.value == page) return

        isRemotePageChange = true
        currentPdfView?.jumpTo(page - 1, withAnimation = false, resetZoom = false, resetHorizontalScroll = false)
        currentPdfView?.centerCurrentPage(withAnimation = false)
        _currentPage.value = page
        _showPageInfo.value = true
    }


    fun getPage(){
        pdfGrpcRepository.sendJoinRequest()
    }

    fun notifyPageChange(page: Int) {
        // 공통: 진도 저장(둘 다)
        scheduleProgressUpdate(page)

        if (!isRemotePageChange && _syncConnected.value) {
            when (_readingMode.value) {
                ReadingMode.FOLLOW -> {
                    // FOLLOW에선 '리더만' PAGE_MOVE 의미있음(서버도 리더만 반영)
                    if (_isCurrentLeader.value) {
                        pdfGrpcRepository.sendPageUpdate(page)
                    }
                }
                ReadingMode.FREE -> {
                    // FREE는 PAGE_MOVE 안 보냄(각자 자유 스크롤)
                    // 아무 것도 안 함
                }
            }
        }
        isRemotePageChange = false
    }

    // 연결 재시도 함수 추가
    fun retrySyncConnection() {
        Log.d(TAG, "PDF 동기화 연결 재시도")
        _syncError.value = null
        pdfGrpcRepository.reconnect()
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    // 1) 하이라이트 추가
    private fun handleRemoteHighlightAdd(sync: HighlightSync) {
        //  내 요청 에코인지 먼저 확인
        val key = PendingHighlightKey(sync.page, sync.snippet, sync.color, sync.startX, sync.startY, sync.endX, sync.endY)
        val tempId = if (sync.userId == myUserId) pendingHighlights.remove(key) else null

        if (tempId != null) {
            // 1) 뷰/상태에서 tempId 지우기
            currentPdfView?.removeHighlightAnnotations(listOf(tempId))
            _annotations.update { st ->
                st.copy(highlights = ArrayList(st.highlights.filterNot { it.id == tempId }))
            }
        }

        // 2) 서버 id로 추가
        val model = HighlightModel(
            id = sync.id, page = sync.page, snippet = sync.snippet, color = sync.color,
            coordinates = UiCoordinates(sync.startX, sync.startY, sync.endX, sync.endY),
            userId = sync.userId
        )

        val filter = _highlightFilterUserIds.value
        if (filter.isEmpty() || sync.userId in filter) {
            currentPdfView?.addHighlight(model) // 또는 addComment
        }
        _annotations.update { it.copy(highlights = ArrayList(it.highlights + model)) }
    }


    // 3) 하이라이트 삭제 (ID 리스트로 호출)
    private fun handleRemoteHighlightDelete(id: Long) {
        currentPdfView?.removeHighlightAnnotations(listOf(id))
        _annotations.update { st -> st.copy(highlights = ArrayList(st.highlights.filterNot { it.id == id })) }
    }

    // 4) 댓글 추가
    private fun handleRemoteCommentAdd(sync: CommentSync) {
        val key = PendingCommentKey(sync.page, sync.snippet, sync.text, sync.startX, sync.startY, sync.endX, sync.endY)
        val tempId = if (sync.userId == myUserId) pendingComments.remove(key) else null

        if (tempId != null) {
            currentPdfView?.removeCommentAnnotations(listOf(tempId))
            _annotations.update { st -> st.copy(comments = ArrayList(st.comments.filterNot { it.id == tempId })) }
        }

        val model = CommentModel(sync.id, sync.snippet, sync.text, sync.page,
            UiCoordinates(sync.startX, sync.startY, sync.endX, sync.endY), userId = sync.userId)

        val filter = _highlightFilterUserIds.value
        if (filter.isEmpty() || sync.userId in filter) {
            currentPdfView?.addComment(model) // 또는 addComment
        }
        _annotations.update { it.copy(comments = ArrayList(it.comments + model)) }
    }

    // 5) 댓글 수정
    private fun handleRemoteCommentUpdate(sync: CommentSync) {
        val original = _annotations.value.comments.find { it.id == sync.id } ?: return
        val updatedModel = original.copy(text = sync.text)
        currentPdfView?.updateComment(updatedModel)
        _annotations.update { state ->
            state.copy(
                comments = ArrayList(
                    state.comments.map { if (it.id == sync.id) updatedModel else it }
                )
            )
        }
    }

    // 6) 댓글 삭제 (ID 리스트로 호출)
    private fun handleRemoteCommentDelete(id: Long) {
        currentPdfView?.removeCommentAnnotations(listOf(id))
        _annotations.update { st -> st.copy(comments = ArrayList(st.comments.filterNot { it.id == id })) }
    }


    override fun onCleared() {
        super.onCleared()
        // Observer 해제
        pdfGrpcRepository.newPageUpdates.removeObserver(pdfPageObserver)
        pdfGrpcRepository.connectionStatus.removeObserver(connectionObserver)
        pdfGrpcRepository.newHighlights.removeObserver(highlightAddObserver)
        pdfGrpcRepository.deletedHighlights.removeObserver(highlightDeleteObserver)
        pdfGrpcRepository.newComments.removeObserver(commentAddObserver)
        pdfGrpcRepository.updatedComments.removeObserver(commentUpdateObserver)
        pdfGrpcRepository.deletedComments.removeObserver(commentDeleteObserver)
        pdfGrpcRepository.joinRequests.removeObserver(joinObserver)
        pdfGrpcRepository.leadershipTransfers.removeObserver(leadershipObserver)
        pdfGrpcRepository.participantsSnapshot.removeObserver(participantsObserver)
        pdfGrpcRepository.progressUpdates.removeObserver(progressObserver)


        // 연결 종료
        pdfGrpcRepository.leaveRoom()
        currentPdfView = null

        Log.d(TAG, "ViewModel 정리 완료")
    }

    // 기존 함수들도 Repository 패턴에 맞게 수정
    fun goToPreviousPage() {
        val newPage = (currentPage.value - 1).coerceAtLeast(1)
        if (newPage != currentPage.value) {
            Log.d(TAG, "이전 페이지로 이동: ${currentPage.value} -> $newPage")
            currentPdfView?.jumpTo(newPage - 1, withAnimation = true)
        }
    }

    fun goToNextPage() {
        val newPage = (currentPage.value + 1).coerceAtMost(totalPages.value)
        if (newPage != currentPage.value) {
            Log.d(TAG, "다음 페이지로 이동: ${currentPage.value} -> $newPage")
            currentPdfView?.jumpTo(newPage - 1, withAnimation = true)
        }
    }

    fun goToPage(page: Int) {
        val targetPage = page.coerceIn(1, totalPages.value)
        if (targetPage != currentPage.value) {
            Log.d(TAG, "특정 페이지로 이동: ${currentPage.value} -> $targetPage")
            currentPdfView?.jumpTo(targetPage - 1, withAnimation = false)
        }
    }

    fun canGoToPreviousPage(): Boolean = currentPage.value > 1
    fun canGoToNextPage(): Boolean = currentPage.value < totalPages.value

    // thumbnail

    fun generateThumbnails() {
        val file = _pdfFile.value ?: return
        val pageCount = _totalPages.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = mutableListOf<Bitmap>()
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fileDescriptor)

                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val width = 120
                    val height = (width * page.height.toFloat() / page.width).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result.add(bitmap)
                    page.close()
                }

                renderer.close()
                fileDescriptor.close()

                _thumbnails.update { result }
            } catch (e: Exception) {
                Log.e(TAG, "썸네일 생성 중 오류 발생: ${e.message}", e)
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


    private fun RpcReadingMode.toLocal(): ReadingMode = when (this) {
        RpcReadingMode.FOLLOW -> ReadingMode.FOLLOW
        RpcReadingMode.FREE   -> ReadingMode.FREE
        else                  -> ReadingMode.FREE // 디폴트(원하는 값으로)
    }

    private fun ReadingMode.toGrpc(): RpcReadingMode = when (this) {
        ReadingMode.FOLLOW -> RpcReadingMode.FOLLOW
        ReadingMode.FREE   -> RpcReadingMode.FREE
    }

}
