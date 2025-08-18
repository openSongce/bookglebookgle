package com.ssafy.bookglebookgle.entity

data class CommentSync(
    val id: Long,
    val page: Int,
    val snippet: String,
    val text: String,
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
    val userId: String
)