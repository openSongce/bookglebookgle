package com.ssafy.bookglebookgle.entity.pdf

import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel

/** Response */
data class PdfNoteResponse(
    val id: Long,
    val title: String,
    val filePath: String? = null,
    val about: String?,
    val groupId: Long?
)

data class CommentResponse(
    val id: Long,
    val snippet: String,
    val text: String,
    val page: Int,
    val coordinates: CoordinatesResponse
)

data class HighlightResponse(
    val id: Long,
    val snippet: String,
    val color: String,
    val page: Int,
    val coordinates: CoordinatesResponse
)

data class BookmarkResponse(
    val id: Long,
    val page: Int,
)

data class CoordinatesResponse(
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double
)

data class StatusMessageResponse(
    val message: String
)

data class DeleteAnnotationResponse(
    val deletedIds: List<Long>
)

data class AnnotationListResponse(
    val comments: List<CommentModel> = emptyList(),
    val highlights: List<HighlightModel> = emptyList(),
    val bookmarks: List<BookmarkModel> = emptyList()
)