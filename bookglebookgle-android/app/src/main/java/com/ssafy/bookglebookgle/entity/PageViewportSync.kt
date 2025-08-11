package com.ssafy.bookglebookgle.entity

data class PageViewportSync(
    val page: Int,
    val userId: String,
    val scaleNorm: Float? = null,   // fitWidth 기준 정규화 스케일(옵션)
    val centerXNorm: Float? = null, // 0..1
    val centerYNorm: Float? = null  // 0..1
) {
    val hasViewport: Boolean get() = scaleNorm != null && centerXNorm != null && centerYNorm != null
}
