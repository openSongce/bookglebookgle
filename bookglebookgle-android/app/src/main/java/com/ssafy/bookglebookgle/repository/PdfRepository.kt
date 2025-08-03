package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.pdf.response.AnnotationListResponse
import com.ssafy.bookglebookgle.pdf.response.DeleteAnnotationResponse
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.Coordinates
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel
import java.io.File
import java.io.InputStream

interface PdfRepository {
    suspend fun uploadPdf(file: File): Boolean
    suspend fun getGroupPdf(groupId: Long): PdfRepositoryImpl.PdfData?

    // 댓글 관리
    suspend fun addComment(
        groupId: Long,
        snippet: String,
        text: String,
        page: Int,
        coordinates: Coordinates
    ): CommentModel?

    suspend fun getComments(groupId: Long): List<CommentModel>?
    suspend fun updateComment(commentId: Long, newText: String): CommentModel?
    suspend fun deleteComment(commentId: Long): DeleteAnnotationResponse?

    // 하이라이트 관리
    suspend fun addHighlight(
        groupId: Long,
        snippet: String,
        color: String,
        page: Int,
        coordinates: Coordinates
    ): HighlightModel?

    suspend fun getHighlights(groupId: Long): List<HighlightModel>?
    suspend fun deleteHighlight(highlightId: Long): DeleteAnnotationResponse?

    // 북마크 관리
    suspend fun addBookmark(groupId: Long, page: Int): BookmarkModel?
    suspend fun getBookmarks(groupId: Long): List<BookmarkModel>?
    suspend fun deleteBookmark(bookmarkId: Long): DeleteAnnotationResponse?
    suspend fun deleteBookmarkByPage(groupId: Long, page: Int): DeleteAnnotationResponse?

    // 전체 주석 조회
    suspend fun getAllAnnotations(pdfId: Long): AnnotationListResponse?
}
