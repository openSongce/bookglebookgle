package com.ssafy.bookglebookgle.entity

data class Participant(
    val userId: String,
    val userName: String,
    val isOriginalHost: Boolean, // 방을 만든 사람인지
    val isCurrentHost: Boolean,  // 현재 방장인지
    val isOnline: Boolean = false,
    val maxReadPage: Int = 0
)