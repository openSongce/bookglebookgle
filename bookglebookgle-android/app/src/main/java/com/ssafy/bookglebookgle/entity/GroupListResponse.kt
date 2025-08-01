package com.ssafy.bookglebookgle.entity

data class GroupListResponse(
    val groupId: Long,
    val roomTitle: String,
    val description: String,
    val category: String,
    val groupMaxNum: Int,
    val currentNum: Int,
    val minimumRating: Int
)
