package com.ssafy.bookglebookgle.entity

/**
 * 페이지만 동기화하는 간단 모델
 */
data class PdfPageSync(
    val page: Int,
    val fromUserId: String
)
