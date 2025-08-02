package com.ssafy.bookglebookgle.entity.pdf

// Request 모델들
data class AddCommentRequest(
    val snippet: String,
    val text: String,
    val page: Int,
    val coordinates: CoordinatesRequest
)

data class UpdateCommentRequest(
    val text: String
)

data class DeleteCommentsRequest(
    val commentIds: List<Long>
)

data class AddHighlightRequest(
    val snippet: String,
    val color: String,
    val page: Int,
    val coordinates: CoordinatesRequest
)

data class DeleteHighlightsRequest(
    val highlightIds: List<Long>
)

data class AddBookmarkRequest(
    val page: Int
)

data class AddTagRequest(
    val title: String,
    val colorCode: String
)

data class CoordinatesRequest(
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double
)