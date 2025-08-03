package com.ssafy.bookglebookgle.entity.pdf

/** Request */

// 댓글 작성 모델
data class AddCommentRequest(
    val snippet: String, // PDF 선택 텍스트
    val text: String, // 댓글 내용
    val page: Int, // 페이지 번호
    val coordinates: CoordinatesRequest // 좌표 정보
)

// 댓글 수정 모델
data class UpdateCommentRequest(
    val text: String
)

// 댓글 삭제 모델
data class DeleteCommentsRequest(
    val commentIds: List<Long>
)

// 하이라이트 추가 모델
data class AddHighlightRequest(
    val snippet: String,
    val color: String,
    val page: Int,
    val coordinates: CoordinatesRequest
)

// 하이라이트 삭제 모델
data class DeleteHighlightsRequest(
    val highlightIds: List<Long>
)

// 북마크 추가 모델
data class AddBookmarkRequest(
    val page: Int
)

// 좌표 정보 모델
data class CoordinatesRequest(
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double
)