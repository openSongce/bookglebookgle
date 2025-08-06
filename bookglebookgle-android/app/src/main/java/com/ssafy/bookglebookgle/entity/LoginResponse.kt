package com.ssafy.bookglebookgle.entity

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val email: String,
    val nickname: String,
    val profileImageUrl: String,
    val avgRating: Float,
    val reviewCount: Int,
    val loginProvider: String,
    val userId: Long
)
