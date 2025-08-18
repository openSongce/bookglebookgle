package com.ssafy.bookglebookgle.entity

/** 하이라이트/댓글 동기화 데이터 */
data class HighlightSync(
    val id: Long,
    val page: Int,
    val snippet: String,
    val color: String,
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
    val userId: String
)