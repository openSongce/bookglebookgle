package com.ssafy.bookglebookgle.entity

data class GroupMemberProgress (
    val userId: Long,
    val userNickName: String,
    val maxReadPage: Int,
    val progressPercent: Int
)