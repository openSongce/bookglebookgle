package com.ssafy.bookglebookgle.entity

data class UserProfileUpdateRequest(
    val nickname: String?,
    val profileImgUrl: String? = null,   // 지금은 안 써도 됨
    val profileColor: String?            // "#AABBCC" 대문자
)