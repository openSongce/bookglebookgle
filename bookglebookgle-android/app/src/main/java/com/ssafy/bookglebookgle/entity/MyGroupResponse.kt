package com.ssafy.bookglebookgle.entity

data class MyGroupResponse(
    val groupId: Long,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val category: String,
    val currentMembers: Int,
    val maxMembers: Int
)
