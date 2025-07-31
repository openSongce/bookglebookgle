package com.ssafy.bookglebookgle.entity

data class GroupDetailResponse(
    val roomTitle: String,
    val category: String,
    val schedule: String,
    val memberCount: Int,
    val maxMemberCount: Int,
    val description: String,
    val photoUrl: String?
)
