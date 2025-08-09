package com.ssafy.bookglebookgle.entity

data class GroupDetailResponse(
    val roomTitle: String,
    val category: String,
    val schedule: String,
    val memberCount: Int,
    val maxMemberCount: Int,
    val description: String,
    val photoUrl: String?,
    val isHost: Boolean,
    val minRequiredRating: Int,
    val pageCount: Int,
    val members: List<GroupMemberDetailDto>
)

data class GroupMemberDetailDto(
    val userId: Long,
    val userNickName: String,
    val profileColor: String,
    val lastPageRead: Int,   // 서버: 0-based
    val progressPercent: Int,
    val isHost: Boolean
)