package com.ssafy.bookglebookgle.entity.pdf

// Response 모델들
data class PdfNoteResponse(
    val id: Long,
    val title: String,
    val filePath: String? = null,
    val about: String?,
    val tagId: Long?,
    val tag: TagResponse?,
    val updatedTime: Long,
    val groupId: Long?
)

data class CommentResponse(
    val id: Long,
    val snippet: String,
    val text: String,
    val page: Int,
    val updatedAt: Long,
    val coordinates: CoordinatesResponse
)

data class HighlightResponse(
    val id: Long,
    val snippet: String,
    val color: String,
    val page: Int,
    val updatedAt: Long,
    val coordinates: CoordinatesResponse
)

data class BookmarkResponse(
    val id: Long,
    val page: Int,
    val updatedAt: Long
)

data class TagResponse(
    val id: Long,
    val title: String,
    val colorCode: String
)

data class CoordinatesResponse(
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double
)

data class PdfNotesResponse(
    val notes: List<PdfNoteResponse>
)

data class StatusMessageResponse(
    val message: String
)

data class DeleteAnnotationResponse(
    val deletedIds: List<Long>
)

data class RemoveTagResponse(
    val tagId: Long
)

data class AnnotationListResponse(
    val comments: List<CommentResponse> = emptyList(),
    val highlights: List<HighlightResponse> = emptyList(),
    val bookmarks: List<BookmarkResponse> = emptyList()
)