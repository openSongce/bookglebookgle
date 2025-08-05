package com.ssafy.bookglebookgle.viewmodel

import android.graphics.PointF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf.exception.ValidationErrorException
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.state.ResponseState
import com.ssafy.bookglebookgle.pdf.tools.OperationsStateHandler
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.repository.PdfRepository
import com.ssafy.bookglebookgle.util.PdfSyncClientManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "싸피_PdfViewModel"

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
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

    fun showCommentPopup(comments: List<CommentModel>, point: PointF) {
        Log.d(TAG, "==== 댓글 팝업 표시 ====")
        Log.d(TAG, "댓글 수: ${comments.size}")
        Log.d(TAG, "위치: (${point.x}, ${point.y})")
        comments.forEachIndexed { index, comment ->
            Log.d(TAG, "댓글 $index: ID=${comment.id}, 텍스트=${comment.text}")
        }
        _selectedComments.value = comments
        _commentPopupPoint.value = point
        _showCommentPopup.value = true
    }

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
    fun addComment(snippet: String, text: String, page: Int, coordinates: Coordinates?) {
        val groupId = currentGroupId
        Log.d(TAG, "==== 댓글 추가 요청 ====")
        Log.d(TAG, "그룹 ID: $groupId")
        Log.d(TAG, "페이지: $page")
        Log.d(TAG, "스니펫: $snippet")
        Log.d(TAG, "텍스트: $text")
        Log.d(TAG, "좌표: $coordinates")

        if (groupId == null) {
            Log.e(TAG, "댓글 추가 실패: 그룹 ID가 설정되지 않음")
            return
        }

        addCommentResponse.load {
            try {
                if (coordinates == null) {
                    Log.e(TAG, "댓글 추가 실패: 좌표 정보가 없음")
                    throw ValidationErrorException(1, "좌표 정보가 필요합니다.")
                }

                Log.d(TAG, "Repository에서 댓글 추가 요청 시작")
                val result = pdfRepository.addComment(groupId, snippet, text, page, coordinates)

                if (result != null) {
                    Log.d(TAG, "댓글 추가 성공")
                    Log.d(TAG, "생성된 댓글: $result")

                    // 로컬 상태 업데이트
                    val currentAnnotations = _annotations.value
                    val updatedComments = currentAnnotations.comments.toMutableList()
                    updatedComments.add(result)
                    _annotations.value = currentAnnotations.copy(comments = ArrayList(updatedComments))

                    Log.d(TAG, "로컬 상태 업데이트 완료 - 총 댓글 수: ${updatedComments.size}")

                    ResponseState.Success(result)
                } else {
                    Log.e(TAG, "댓글 추가 실패: 서버에서 null 응답")
                    throw Exception("댓글 추가에 실패했습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "댓글 추가 예외 발생", e)
                throw e
            }
        }
    }

    /**
     * 댓글 수정
     */
    fun updateComment(commentId: Long, newText: String) {
        Log.d(TAG, "==== 댓글 수정 요청 ====")
        Log.d(TAG, "댓글 ID: $commentId")
        Log.d(TAG, "새 텍스트: $newText")

        updateCommentResponse.load {
            try {
                Log.d(TAG, "Repository에서 댓글 수정 요청 시작")
                val result = pdfRepository.updateComment(commentId, newText)

                if (result != null) {
                    Log.d(TAG, "댓글 수정 성공")
                    Log.d(TAG, "수정된 댓글: $result")

                    // 로컬 상태 업데이트
                    val currentAnnotations = _annotations.value
                    val updatedComments = currentAnnotations.comments.map { comment ->
                        if (comment.id == commentId) {
                            comment.copy(text = newText)
                        } else {
                            comment
                        }
                    }
                    _annotations.value = currentAnnotations.copy(comments = ArrayList(updatedComments))

                    Log.d(TAG, "로컬 상태 업데이트 완료")

                    ResponseState.Success(result)
                } else {
                    Log.e(TAG, "댓글 수정 실패: 서버에서 null 응답")
                    throw Exception("댓글 수정에 실패했습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "댓글 수정 예외 발생", e)
                throw e
            }
        }
    }

    /**
     * 댓글 삭제
     */
    fun deleteComment(commentId: Long) {
        Log.d(TAG, "==== 댓글 삭제 요청 ====")
        Log.d(TAG, "댓글 ID: $commentId")

        deleteCommentResponse.load {
            try {
                Log.d(TAG, "Repository에서 댓글 삭제 요청 시작")
                val result = pdfRepository.deleteComment(commentId)

                if (result != null) {
                    Log.d(TAG, "댓글 삭제 성공")
                    Log.d(TAG, "삭제된 ID들: ${result.deletedIds}")

                    // 로컬 상태 업데이트
                    removeCommentsFromLocal(result.deletedIds)

                    ResponseState.Success(result)
                } else {
                    Log.e(TAG, "댓글 삭제 실패: 서버에서 null 응답")
                    throw Exception("댓글 삭제에 실패했습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "댓글 삭제 예외 발생", e)
                throw e
            }
        }
    }

    // ========== 하이라이트 관리 ==========

    /**
     * 하이라이트 추가
     */
    fun addHighlight(snippet: String, color: String, page: Int, coordinates: Coordinates) {
        val groupId = currentGroupId
        Log.d(TAG, "==== 하이라이트 추가 요청 ====")
        Log.d(TAG, "그룹 ID: $groupId")
        Log.d(TAG, "페이지: $page")
        Log.d(TAG, "색상: $color")
        Log.d(TAG, "스니펫: $snippet")
        Log.d(TAG, "좌표: $coordinates")

        if (groupId == null) {
            Log.e(TAG, "하이라이트 추가 실패: 그룹 ID가 설정되지 않음")
            return
        }

        addHighlightResponse.load {
            try {
                Log.d(TAG, "Repository에서 하이라이트 추가 요청 시작")
                val result = pdfRepository.addHighlight(groupId, snippet, color, page, coordinates)

                if (result != null) {
                    Log.d(TAG, "하이라이트 추가 성공")
                    Log.d(TAG, "생성된 하이라이트: $result")

                    // 로컬 상태 업데이트
                    val currentAnnotations = _annotations.value
                    val updatedHighlights = currentAnnotations.highlights.toMutableList()
                    updatedHighlights.add(result)
                    _annotations.value = currentAnnotations.copy(highlights = ArrayList(updatedHighlights))

                    Log.d(TAG, "로컬 상태 업데이트 완료 - 총 하이라이트 수: ${updatedHighlights.size}")

                    ResponseState.Success(result)
                } else {
                    Log.e(TAG, "하이라이트 추가 실패: 서버에서 null 응답")
                    throw Exception("하이라이트 추가에 실패했습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "하이라이트 추가 예외 발생", e)
                throw e
            }
        }
    }

    /**
     * 하이라이트 삭제
     */
    fun deleteHighlight(highlightId: Long) {
        Log.d(TAG, "==== 하이라이트 삭제 요청 ====")
        Log.d(TAG, "하이라이트 ID: $highlightId")

        deleteHighlightResponse.load {
            try {
                Log.d(TAG, "Repository에서 하이라이트 삭제 요청 시작")
                val result = pdfRepository.deleteHighlight(highlightId)

                if (result != null) {
                    Log.d(TAG, "하이라이트 삭제 성공")
                    Log.d(TAG, "삭제된 ID들: ${result.deletedIds}")

                    // 로컬 상태 업데이트
                    removeHighlightsFromLocal(result.deletedIds)

                    ResponseState.Success(result)
                } else {
                    Log.e(TAG, "하이라이트 삭제 실패: 서버에서 null 응답")
                    throw Exception("하이라이트 삭제에 실패했습니다.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "하이라이트 삭제 예외 발생", e)
                throw e
            }
        }
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
    private var currentPdfView: com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView? = null
    private var isRemotePageChange = false

    // PDFView 참조 저장
    fun setPdfView(pdfView: com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView) {
        Log.d(TAG, "PDFView 참조 저장")
        this.currentPdfView = pdfView
    }

    // gRPC 연결 시작 (수정된 버전)
    fun connectToSync(groupId: Long, userId: String, onReceive: (com.example.bookglebookgleserver.pdf.grpc.SyncMessage) -> Unit) {
        this.currentGroupId = groupId
        this.userId = userId

        PdfSyncClientManager.connect(groupId, userId, onReceive)
    }

    // 실제 페이지 이동 함수 추가
    fun moveToPage(page: Int) {
        currentPdfView?.let { pdfView ->
            try {
                Log.d(TAG, "페이지 이동 실행: $page")
                // PDF 뷰어에서 실제 페이지 이동 (0 기반 인덱스)
                pdfView.jumpTo(page - 1)
            } catch (e: Exception) {
                Log.e(TAG, "페이지 이동 실패", e)
            }
        } ?: Log.w(TAG, "PDFView가 null - 페이지 이동 불가")
    }

    // 원격 페이지 변경 처리
    fun handleRemotePageChange(page: Int) {
        Log.d(TAG, "원격 페이지 변경 처리: $page")
        isRemotePageChange = true
        moveToPage(page)
    }

    // 페이지 변경 발생 시 서버에 알림 (수정된 버전)
    fun notifyPageChange(page: Int) {
        if (!isRemotePageChange) {
            // 로컬 페이지 변경일 때만 broadcast
            Log.d(TAG, "로컬 페이지 변경 broadcast: $page")
            PdfSyncClientManager.sendPageUpdate(page)
        } else {
            Log.d(TAG, "원격 페이지 변경이므로 broadcast 생략: $page")
        }
        isRemotePageChange = false // 플래그 리셋
    }

    // 뷰모델이 사라질 때 연결 종료
    override fun onCleared() {
        Log.d(TAG, "ViewModel 정리 - gRPC 연결 종료")
        PdfSyncClientManager.disconnect()
        currentPdfView = null
        super.onCleared()
    }


}

//package com.ssafy.bookglebookgle.viewmodel
//
//import android.graphics.PointF
//import android.util.Log
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.ssafy.bookglebookgle.pdf.exception.ValidationErrorException
//import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
//import com.ssafy.bookglebookgle.pdf.state.ResponseState
//import com.ssafy.bookglebookgle.pdf.tools.OperationsStateHandler
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
//import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
//import com.ssafy.bookglebookgle.repository.PdfRepository
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.launch
//import okhttp3.ResponseBody
//import retrofit2.Response
//import java.io.File
//import javax.inject.Inject
//
//private const val TAG = "싸피_PdfViewModel"
//
//@HiltViewModel
//class PdfViewModel @Inject constructor(
//    private val pdfRepository: PdfRepository,
//): ViewModel(){
//
//    var currentGroupId: Long? = null
//        private set
//
//    // ========== PDF 파일 관련 상태 ==========
//
//    private val _pdfFile = MutableStateFlow<File?>(null)
//    val pdfFile: StateFlow<File?> = _pdfFile.asStateFlow()
//
//    private val _pdfTitle = MutableStateFlow("")
//    val pdfTitle: StateFlow<String> = _pdfTitle.asStateFlow()
//
//    private val _isLoading = MutableStateFlow(false)
//    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
//
//    private val _pdfLoadError = MutableStateFlow<String?>(null)
//    val pdfLoadError: StateFlow<String?> = _pdfLoadError.asStateFlow()
//
//    private val _pdfReady = MutableStateFlow(false)
//    val pdfReady: StateFlow<Boolean> = _pdfReady.asStateFlow()
//
//    // ========== PDF 뷰어 상태 ==========
//
//    private val _currentPage = MutableStateFlow(1)
//    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()
//
//    private val _totalPages = MutableStateFlow(1)
//    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()
//
//    private val _showPageInfo = MutableStateFlow(false)
//    val showPageInfo: StateFlow<Boolean> = _showPageInfo.asStateFlow()
//
//    // ========== 텍스트 선택 및 주석 상태 ==========
//
//    private val _showTextSelectionOptions = MutableStateFlow(false)
//    val showTextSelectionOptions: StateFlow<Boolean> = _showTextSelectionOptions.asStateFlow()
//
//    private val _textSelectionPoint = MutableStateFlow(PointF(0f, 0f))
//    val textSelectionPoint: StateFlow<PointF> = _textSelectionPoint.asStateFlow()
//
//    private val _selectedComments = MutableStateFlow<List<CommentModel>>(emptyList())
//    val selectedComments: StateFlow<List<CommentModel>> = _selectedComments.asStateFlow()
//
//    private val _commentPopupPoint = MutableStateFlow(PointF(0f, 0f))
//    val commentPopupPoint: StateFlow<PointF> = _commentPopupPoint.asStateFlow()
//
//    private val _showCommentPopup = MutableStateFlow(false)
//    val showCommentPopup: StateFlow<Boolean> = _showCommentPopup.asStateFlow()
//
//    // ========== 주석 데이터 ==========
//
//    private val _annotations = MutableStateFlow(AnnotationListResponse())
//    val annotations: StateFlow<AnnotationListResponse> = _annotations.asStateFlow()
//
//    // ========== 상태 핸들러들 ==========
//
//    val addCommentResponse = OperationsStateHandler(viewModelScope)
//    val updateCommentResponse = OperationsStateHandler(viewModelScope)
//    val deleteCommentResponse = OperationsStateHandler(viewModelScope)
//
//    val addHighlightResponse = OperationsStateHandler(viewModelScope)
//    val deleteHighlightResponse = OperationsStateHandler(viewModelScope)
//
//    val addBookmarkResponse = OperationsStateHandler(viewModelScope)
//    val deleteBookmarkResponse = OperationsStateHandler(viewModelScope)
//
//    val annotationListResponse = OperationsStateHandler(viewModelScope)
//
//    /**
//     * 그룹 ID 설정
//     */
//    fun setGroupId(groupId: Long) {
//        Log.d(TAG, "그룹 ID 설정: $groupId")
//        currentGroupId = groupId
//    }
//
//    /**
//     * 그룹 PDF 파일을 서버에서 다운로드
//     */
//    fun loadGroupPdf(groupId: Long, context: android.content.Context) {
//        Log.d(TAG, "PDF 다운로드 시작 - 그룹 ID: $groupId")
//
//        _isLoading.value = true
//        _pdfLoadError.value = null
//
//        viewModelScope.launch {
//            try {
//                val pdfData = pdfRepository.getGroupPdf(groupId)
//
//                if (pdfData != null && pdfData.inputStream != null) {
//                    Log.d(TAG, "PDF 다운로드 성공 - 파일명: ${pdfData.fileName}")
//
//                    // PDF 제목 설정 (확장자 제거)
//                    _pdfTitle.value = pdfData.fileName.removeSuffix(".pdf")
//
//                    // PDF 처리
//                    val bytes = pdfData.inputStream.readBytes()
//                    Log.d(TAG, "PDF 크기: ${bytes.size} bytes")
//
//                    if (bytes.size >= 4 && String(bytes.sliceArray(0..3)) == "%PDF") {
//                        val tempFile = File.createTempFile("group_pdf", ".pdf", context.cacheDir)
//                        tempFile.writeBytes(bytes)
//
//                        Log.d(TAG, "임시 파일 저장: ${tempFile.absolutePath}")
//                        _pdfFile.value = tempFile
//                        _pdfReady.value = true
//                        _isLoading.value = false
//                    } else {
//                        Log.e(TAG, "유효하지 않은 PDF 파일")
//                        _pdfLoadError.value = "유효하지 않은 PDF 파일"
//                        _isLoading.value = false
//                        _pdfReady.value = false
//                    }
//                } else {
//                    Log.e(TAG, "PDF 파일을 다운로드할 수 없습니다.")
//                    _pdfLoadError.value = "PDF 파일을 다운로드할 수 없습니다."
//                    _isLoading.value = false
//                    _pdfReady.value = false
//                }
//            } catch (e: Exception) {
//                val error = "PDF 다운로드 중 오류가 발생했습니다: ${e.message}"
//                Log.e(TAG, error, e)
//                _pdfLoadError.value = error
//                _isLoading.value = false
//                _pdfReady.value = false
//            }
//        }
//    }
//
////    /**
////     * 그룹 PDF 파일을 서버에서 다운로드
////     */
////    fun loadGroupPdf(groupId: Long, context: android.content.Context) {
////        Log.d(TAG, "PDF 다운로드 시작 - 그룹 ID: $groupId")
////
////        _isLoading.value = true
////        _pdfLoadError.value = null
////
////        viewModelScope.launch {
////            try {
////                val inputStream = pdfRepository.getGroupPdf(groupId)
////                if (inputStream != null) {
////                    Log.d(TAG, "PDF 다운로드 성공")
////
////                    // 헤더에서 파일명 추출
////                    val fileName = extractFileNameFromResponse(inputStream)
////                    _pdfTitle.value = fileName.removeSuffix(".pdf")
////
////                    // PDF 처리
////                    val bytes = inputStream.readBytes()
////                    Log.d(TAG, "PDF 크기: ${bytes.size} bytes")
////
////                    if (bytes.size >= 4 && String(bytes.sliceArray(0..3)) == "%PDF") {
////                        val tempFile = File.createTempFile("group_pdf", ".pdf", context.cacheDir)
////                        tempFile.writeBytes(bytes)
////
////                        Log.d(TAG, "임시 파일 저장: ${tempFile.absolutePath}")
////                        _pdfFile.value = tempFile
////                        _pdfReady.value = true
////                        _isLoading.value = false
////                    } else {
////                        Log.e(TAG, "유효하지 않은 PDF 파일")
////                        _pdfLoadError.value = "유효하지 않은 PDF 파일"
////                        _isLoading.value = false
////                        _pdfReady.value = false
////                    }
////                } else {
////                    Log.e(TAG, "PDF 파일을 다운로드할 수 없습니다.")
////                    _pdfLoadError.value = "PDF 파일을 다운로드할 수 없습니다."
////                    _isLoading.value = false
////                    _pdfReady.value = false
////                }
////            } catch (e: Exception) {
////                val error = "PDF 다운로드 중 오류가 발생했습니다: ${e.message}"
////                Log.e(TAG, error, e)
////                _pdfLoadError.value = error
////                _isLoading.value = false
////                _pdfReady.value = false
////            }
////        }
////    }
//
//    // ========== PDF 뷰어 상태 관리 ==========
//
//    fun updateCurrentPage(page: Int) {
//        Log.d(TAG, "현재 페이지 업데이트: $page")
//        _currentPage.value = page
//        _showPageInfo.value = true
//    }
//
//    fun updateTotalPages(pages: Int) {
//        Log.d(TAG, "총 페이지 수 업데이트: $pages")
//        _totalPages.value = pages
//    }
//
//    fun hidePageInfo() {
//        Log.d(TAG, "페이지 정보 숨김")
//        _showPageInfo.value = false
//    }
//
//    // ========== 텍스트 선택 관리 ==========
//
//    fun showTextSelection(point: PointF) {
//        Log.d(TAG, "텍스트 선택 표시 - 위치: (${point.x}, ${point.y})")
//        _textSelectionPoint.value = point
//        _showTextSelectionOptions.value = true
//    }
//
//    fun hideTextSelection() {
//        Log.d(TAG, "텍스트 선택 숨김")
//        _showTextSelectionOptions.value = false
//        _textSelectionPoint.value = PointF(0f, 0f)
//    }
//
//    // ========== 댓글 팝업 관리 ==========
//
//    fun showCommentPopup(comments: List<CommentModel>, point: PointF) {
//        Log.d(TAG, "댓글 팝업 표시 - 댓글 수: ${comments.size}, 위치: (${point.x}, ${point.y})")
//        _selectedComments.value = comments
//        _commentPopupPoint.value = point
//        _showCommentPopup.value = true
//    }
//
//    fun hideCommentPopup() {
//        Log.d(TAG, "댓글 팝업 숨김")
//        _showCommentPopup.value = false
//        _selectedComments.value = emptyList()
//        _commentPopupPoint.value = PointF(0f, 0f)
//    }
//
//    // ========== 댓글 관리 ==========
//
//    /**
//     * 댓글 추가
//     */
//    fun addComment(snippet: String, text: String, page: Int, coordinates: Coordinates?) {
//        val groupId = currentGroupId
//        Log.d(TAG, "댓글 추가 시도 - 그룹 ID: $groupId, 페이지: $page, 텍스트: $text")
//
//        if (groupId == null) {
//            Log.e(TAG, "댓글 추가 실패: 그룹 ID가 설정되지 않음")
//            return
//        }
//
//        addCommentResponse.load {
//            try {
//                if (coordinates == null) {
//                    Log.e(TAG, "댓글 추가 실패: 좌표 정보가 없음")
//                    throw ValidationErrorException(1, "좌표 정보가 필요합니다.")
//                }
//
//                val result = pdfRepository.addComment(groupId, snippet, text, page, coordinates)
//                if (result != null) {
//                    Log.d(TAG, "댓글 추가 성공 - ID: ${result.id}")
//
//                    // 로컬 상태 업데이트
//                    val currentAnnotations = _annotations.value
//                    val updatedComments = currentAnnotations.comments.toMutableList()
//                    updatedComments.add(result)
//                    _annotations.value = currentAnnotations.copy(comments = ArrayList(updatedComments))
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "댓글 추가 실패: 서버에서 null 응답")
//                    throw Exception("댓글 추가에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "댓글 추가 예외 발생", e)
//                throw e
//            }
//        }
//    }
//
//    /**
//     * 댓글 수정
//     */
//    fun updateComment(commentId: Long, newText: String) {
//        Log.d(TAG, "댓글 수정 시도 - ID: $commentId, 새 텍스트: $newText")
//
//        updateCommentResponse.load {
//            try {
//                val result = pdfRepository.updateComment(commentId, newText)
//                if (result != null) {
//                    Log.d(TAG, "댓글 수정 성공 - ID: $commentId")
//
//                    // 로컬 상태 업데이트
//                    val currentAnnotations = _annotations.value
//                    val updatedComments = currentAnnotations.comments.map { comment ->
//                        if (comment.id == commentId) {
////                            comment.copy(text = newText, updatedAt = result.updatedAt)
//                        } else {
//                            comment
//                        }
//                    }
////                    _annotations.value = currentAnnotations.copy(comments = ArrayList(updatedComments))
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "댓글 수정 실패: 서버에서 null 응답")
//                    throw Exception("댓글 수정에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "댓글 수정 예외 발생", e)
//                throw e
//            }
//        }
//    }
//
//    /**
//     * 댓글 삭제
//     */
//    fun deleteComment(commentId: Long) {
//        Log.d(TAG, "댓글 삭제 시도 - ID: $commentId")
//
//        deleteCommentResponse.load {
//            try {
//                val result = pdfRepository.deleteComment(commentId)
//                if (result != null) {
//                    Log.d(TAG, "댓글 삭제 성공 - 삭제된 ID: ${result.deletedIds}")
//
//                    // 로컬 상태 업데이트
//                    removeCommentsFromLocal(result.deletedIds)
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "댓글 삭제 실패: 서버에서 null 응답")
//                    throw Exception("댓글 삭제에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "댓글 삭제 예외 발생", e)
//                throw e
//            }
//        }
//    }
//
//    // ========== 하이라이트 관리 ==========
//
//    /**
//     * 하이라이트 추가
//     */
//    fun addHighlight(snippet: String, color: String, page: Int, coordinates: Coordinates) {
//        val groupId = currentGroupId
//        Log.d(TAG, "하이라이트 추가 시도 - 그룹 ID: $groupId, 페이지: $page, 색상: $color")
//
//        if (groupId == null) {
//            Log.e(TAG, "하이라이트 추가 실패: 그룹 ID가 설정되지 않음")
//            return
//        }
//
//        addHighlightResponse.load {
//            try {
//                val result = pdfRepository.addHighlight(groupId, snippet, color, page, coordinates)
//                if (result != null) {
//                    Log.d(TAG, "하이라이트 추가 성공 - ID: ${result.id}")
//
//                    // 로컬 상태 업데이트
//                    val currentAnnotations = _annotations.value
//                    val updatedHighlights = currentAnnotations.highlights.toMutableList()
//                    updatedHighlights.add(result)
//                    _annotations.value = currentAnnotations.copy(highlights = ArrayList(updatedHighlights))
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "하이라이트 추가 실패: 서버에서 null 응답")
//                    throw Exception("하이라이트 추가에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "하이라이트 추가 예외 발생", e)
//                throw e
//            }
//        }
//    }
//
//    /**
//     * 하이라이트 삭제
//     */
//    fun deleteHighlight(highlightId: Long) {
//        Log.d(TAG, "하이라이트 삭제 시도 - ID: $highlightId")
//
//        deleteHighlightResponse.load {
//            try {
//                val result = pdfRepository.deleteHighlight(highlightId)
//                if (result != null) {
//                    Log.d(TAG, "하이라이트 삭제 성공 - 삭제된 ID: ${result.deletedIds}")
//
//                    // 로컬 상태 업데이트
//                    removeHighlightsFromLocal(result.deletedIds)
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "하이라이트 삭제 실패: 서버에서 null 응답")
//                    throw Exception("하이라이트 삭제에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "하이라이트 삭제 예외 발생", e)
//                throw e
//            }
//        }
//    }
//
//    // ========== 북마크 관리 ==========
//
//    /**
//     * 북마크 추가
//     */
//    fun addBookmark(page: Int) {
//        val groupId = currentGroupId
//        Log.d(TAG, "북마크 추가 시도 - 그룹 ID: $groupId, 페이지: $page")
//
//        if (groupId == null) {
//            Log.e(TAG, "북마크 추가 실패: 그룹 ID가 설정되지 않음")
//            return
//        }
//
//        addBookmarkResponse.load {
//            try {
//                val result = pdfRepository.addBookmark(groupId, page)
//                if (result != null) {
//                    Log.d(TAG, "북마크 추가 성공 - ID: ${result.id}, 페이지: ${result.page}")
//
//                    // 로컬 상태 업데이트
//                    val currentAnnotations = _annotations.value
//                    val updatedBookmarks = currentAnnotations.bookmarks.toMutableList()
//                    updatedBookmarks.add(result)
//                    _annotations.value = currentAnnotations.copy(bookmarks = ArrayList(updatedBookmarks))
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "북마크 추가 실패: 서버에서 null 응답")
//                    throw Exception("북마크 추가에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "북마크 추가 예외 발생", e)
//                throw e
//            }
//        }
//    }
//
//    /**
//     * 북마크 삭제 (페이지 기준)
//     */
//    fun removeBookmark(page: Int) {
//        val groupId = currentGroupId
//        Log.d(TAG, "북마크 삭제 시도 (페이지 기준) - 그룹 ID: $groupId, 페이지: $page")
//
//        if (groupId == null) {
//            Log.e(TAG, "북마크 삭제 실패: 그룹 ID가 설정되지 않음")
//            return
//        }
//
//        deleteBookmarkResponse.load {
//            try {
//                val result = pdfRepository.deleteBookmarkByPage(groupId, page)
//                if (result != null) {
//                    Log.d(TAG, "북마크 삭제 성공 (페이지 기준) - 삭제된 ID: ${result.deletedIds}")
//
//                    // 로컬 상태 업데이트
//                    removeBookmarksFromLocal(result.deletedIds)
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "북마크 삭제 실패: 서버에서 null 응답")
//                    throw Exception("북마크 삭제에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "북마크 삭제 예외 발생 (페이지 기준)", e)
//                throw e
//            }
//        }
//    }
//
//    /**
//     * 북마크 삭제 (ID 기준)
//     */
//    fun deleteBookmark(bookmarkId: Long) {
//        Log.d(TAG, "북마크 삭제 시도 (ID 기준) - ID: $bookmarkId")
//
//        deleteBookmarkResponse.load {
//            try {
//                val result = pdfRepository.deleteBookmark(bookmarkId)
//                if (result != null) {
//                    Log.d(TAG, "북마크 삭제 성공 (ID 기준) - 삭제된 ID: ${result.deletedIds}")
//
//                    // 로컬 상태 업데이트
//                    removeBookmarksFromLocal(result.deletedIds)
//
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "북마크 삭제 실패: 서버에서 null 응답")
//                    throw Exception("북마크 삭제에 실패했습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "북마크 삭제 예외 발생 (ID 기준)", e)
//                throw e
//            }
//        }
//    }
//
//    /**
//     * 현재 페이지에 북마크가 있는지 확인
//     */
//    fun hasBookmarkOnPage(page: Int): Boolean {
//        val hasBookmark = _annotations.value.bookmarks.any { it.page == page }
//        Log.d(TAG, "페이지 $page 북마크 존재 여부: $hasBookmark")
//        return hasBookmark
//    }
//
//    /**
//     * 특정 페이지의 북마크 ID 가져오기
//     */
//    fun getBookmarkIdForPage(page: Int): Long? {
//        val bookmarkId = _annotations.value.bookmarks.find { it.page == page }?.id
//        Log.d(TAG, "페이지 $page 북마크 ID: $bookmarkId")
//        return bookmarkId
//    }
//
//    /**
//     * 북마크 토글
//     */
//    fun toggleBookmark(page: Int) {
//        Log.d(TAG, "북마크 토글 - 페이지: $page")
//
//        if (hasBookmarkOnPage(page)) {
//            getBookmarkIdForPage(page)?.let { bookmarkId ->
//                Log.d(TAG, "기존 북마크 삭제 - ID: $bookmarkId")
//                deleteBookmark(bookmarkId)
//            }
//        } else {
//            Log.d(TAG, "새 북마크 추가 - 페이지: $page")
//            addBookmark(page)
//        }
//    }
//
//    // ========== 전체 주석 관리 ==========
//
//    /**
//     * 모든 주석 로드
//     */
//    fun loadAllAnnotations() {
//        val groupId = currentGroupId
//        Log.d(TAG, "모든 주석 로드 시도 - 그룹 ID: $groupId")
//
//        if (groupId == null) {
//            Log.e(TAG, "주석 로드 실패: 그룹 ID가 설정되지 않음")
//            return
//        }
//
//        annotationListResponse.load {
//            try {
//                val result = pdfRepository.getAllAnnotations(groupId)
//                if (result != null) {
//                    Log.d(TAG, "주석 로드 성공 - 댓글: ${result.comments.size}개, 하이라이트: ${result.highlights.size}개, 북마크: ${result.bookmarks.size}개")
//                    _annotations.value = result
//                    ResponseState.Success(result)
//                } else {
//                    Log.e(TAG, "주석 로드 실패: 서버에서 null 응답")
//                    throw Exception("주석 목록을 불러올 수 없습니다.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "주석 로드 예외 발생", e)
//                throw e
//            }
//        }
//    }
//
//    // ========== 상태 초기화 ==========
//
//    fun resetPdfState() {
//        Log.d(TAG, "PDF 상태 초기화")
//
//        _pdfFile.value = null
//        _pdfTitle.value = ""
//        _isLoading.value = false
//        _pdfLoadError.value = null
//        _pdfReady.value = false
//        _currentPage.value = 1
//        _totalPages.value = 1
//        _showPageInfo.value = false
//        hideTextSelection()
//        hideCommentPopup()
//    }
//
//    // ========== 로컬 상태 업데이트 헬퍼 메서드 ==========
//
//    private fun removeCommentsFromLocal(deletedIds: List<Long>) {
//        Log.d(TAG, "로컬에서 댓글 제거: $deletedIds")
//        val currentAnnotations = _annotations.value
//        val beforeCount = currentAnnotations.comments.size
//        val updatedComments = currentAnnotations.comments.filter { comment ->
//            !deletedIds.contains(comment.id)
//        }
//        _annotations.value = currentAnnotations.copy(comments = ArrayList(updatedComments))
//        Log.d(TAG, "댓글 제거 완료 - 이전: ${beforeCount}개, 현재: ${updatedComments.size}개")
//    }
//
//    private fun removeHighlightsFromLocal(deletedIds: List<Long>) {
//        Log.d(TAG, "로컬에서 하이라이트 제거: $deletedIds")
//        val currentAnnotations = _annotations.value
//        val beforeCount = currentAnnotations.highlights.size
//        val updatedHighlights = currentAnnotations.highlights.filter { highlight ->
//            !deletedIds.contains(highlight.id)
//        }
//        _annotations.value = currentAnnotations.copy(highlights = ArrayList(updatedHighlights))
//        Log.d(TAG, "하이라이트 제거 완료 - 이전: ${beforeCount}개, 현재: ${updatedHighlights.size}개")
//    }
//
//    private fun removeBookmarksFromLocal(deletedIds: List<Long>) {
//        Log.d(TAG, "로컬에서 북마크 제거: $deletedIds")
//        val currentAnnotations = _annotations.value
//        val beforeCount = currentAnnotations.bookmarks.size
//        val updatedBookmarks = currentAnnotations.bookmarks.filter { bookmark ->
//            !deletedIds.contains(bookmark.id)
//        }
//        _annotations.value = currentAnnotations.copy(bookmarks = ArrayList(updatedBookmarks))
//        Log.d(TAG, "북마크 제거 완료 - 이전: ${beforeCount}개, 현재: ${updatedBookmarks.size}개")
//    }
//
//    private fun extractFileNameFromResponse(response: Response<ResponseBody>): String {
//        // Content-Disposition 헤더에서 파일명 추출
//        val contentDisposition = response.headers()["Content-Disposition"]
//        Log.d(TAG, "Content-Disposition: $contentDisposition")
//
//        return if (contentDisposition != null) {
//            // filename="example.pdf" 또는 filename*=UTF-8''example.pdf 형태에서 추출
//            val fileNameRegex = """filename[*]?=['"]?([^'";]+)['"]?""".toRegex()
//            val matchResult = fileNameRegex.find(contentDisposition)
//            val fileName = matchResult?.groupValues?.get(1) ?: "Group PDF"
//
//            // URL 디코딩이 필요한 경우
//            try {
//                java.net.URLDecoder.decode(fileName, "UTF-8")
//            } catch (e: Exception) {
//                fileName
//            }
//        } else {
//            // 헤더에 파일명이 없으면 기본값
//            "Group PDF"
//        }
//    }
//}
