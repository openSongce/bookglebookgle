package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf.exception.ValidationErrorException
import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.response.PdfNoteListModel
import com.ssafy.bookglebookgle.pdf.repository.PDFRepository
import com.ssafy.bookglebookgle.pdf.tools.OperationsStateHandler
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.repository.PdfRepository
import com.ssafy.bookglebookgle.repository.PdfRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfRepository: PdfRepository,
): ViewModel(){

    var pdfDetails: PdfNoteListModel? = null
    var annotations = AnnotationListResponse()

    val addCommentResponse = OperationsStateHandler(viewModelScope)
    val addHighlightResponse = OperationsStateHandler(viewModelScope)
    val addBookmarkResponse = OperationsStateHandler(viewModelScope)
    val removeBookmarkResponse = OperationsStateHandler(viewModelScope)
    val annotationListResponse = OperationsStateHandler(viewModelScope)
    val deleteCommentResponse = OperationsStateHandler(viewModelScope)
    val updateCommentResponse = OperationsStateHandler(viewModelScope)
    val pdfDeleteResponse = OperationsStateHandler(viewModelScope)
    val pdfDownloadResponse = OperationsStateHandler(viewModelScope)

    /**
     * 그룹 PDF 파일을 서버에서 다운로드
     */
    fun loadGroupPdf(groupId: Long, onSuccess: (InputStream) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val inputStream = pdfRepository.getGroupPdf(groupId)
                if (inputStream != null) {
                    onSuccess(inputStream)
                } else {
                    onError("PDF 파일을 다운로드할 수 없습니다.")
                }
            } catch (e: Exception) {
                onError("PDF 다운로드 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

//    fun loadAllAnnotations() {
//        if (pdfDetails == null) return
//        annotationListResponse.load {
//            pdfRepository.getAllAnnotations(pdfDetails!!.id)
//        }
//    }
//
//    fun removeComments(ids: List<Long>){
//        annotations.comments.removeAll { ids.contains(it.id) }
//    }
//    fun removeHighlight(ids: List<Long>){
//        annotations.highlights.removeAll { ids.contains(it.id) }
//    }
//    fun removeBookmarks(ids: List<Long>){
//        annotations.bookmarks.removeAll { ids.contains(it.id) }
//    }
//    fun addComment(snippet: String, text: String, page: Int, coordinates: Coordinates?) {
//        if (pdfDetails == null) return
//        addCommentResponse.load {
//            if (coordinates == null) throw ValidationErrorException(1,"Coordinates not found")
//            pdfRepository.addComment(
//                pdfDetails!!.id,
//                snippet,
//                text,
//                page,
//                coordinates
//            )
//        }
//    }
//    fun addHighlight(snippet: String, color: String, page: Int, coordinates: Coordinates) {
//        if (pdfDetails == null) return
//        addHighlightResponse.load {
//            pdfRepository.addHighlight(
//                pdfDetails!!.id,
//                snippet,
//                color,
//                page,
//                coordinates
//            )
//        }
//    }
//    fun addBookmark(page: Int) {
//        if (pdfDetails == null) return
//        addBookmarkResponse.load {
//            pdfRepository.addBookmark(
//                pdfDetails!!.id,
//                page,
//            )
//        }
//    }
//    fun removeBookmark(page: Int) {
//        if (pdfDetails == null) return
//        removeBookmarkResponse.load {
//            pdfRepository.deleteBookmarkWithPageAndPdfId(
//                page,
//                pdfDetails!!.id,
//            )
//        }
//    }
//
//    fun updateComment(commentId: Long, newText: String) {
//        updateCommentResponse.load {
//            pdfRepository.updateComment(commentId, newText)
//        }
//    }
//    fun deleteComment(commentId: Long) {
//        deleteCommentResponse.load {
//            pdfRepository.deleteComments(listOf(commentId))
//        }
//    }
//    fun deletePdf() {
//        if (pdfDetails == null) return
//        pdfDeleteResponse.load {
//            pdfRepository.deletePdf(pdfDetails!!.id)
//        }
//    }
}