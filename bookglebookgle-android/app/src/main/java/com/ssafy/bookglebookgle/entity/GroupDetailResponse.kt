package com.ssafy.bookglebookgle.entity

data class GroupDetailResponse(
    val roomTitle: String,
    val category: String,
    val schedule: String,
    val memberCount: Int,
    val maxMemberCount: Int,
    val description: String,
    val isHost: Boolean,
    val minRequiredRating: Int,
    val pageCount: Int,
    val members: List<GroupMemberDetailDto>,
    val allMembersCompleted: Boolean
)

data class GroupMemberDetailDto(
    val userId: Long,
    val userNickName: String,
    val profileColor: String?,
    val maxReadPage: Int,   // 서버: 0-based
    val progressPercent: Int,
    val isHost: Boolean,
    val ratingSubmitted: Boolean,
    val profileImageUrl: String?,
    val ratedUserIds : List<Long>
)

data class GroupDetail(
    val roomTitle: String,
    val category: String,
    val schedule: String,
    val memberCount: Int,
    val maxMemberCount: Int,
    val description: String,
    val isHost: Boolean,
    val isCompleted: Boolean,          // 모임 종료 여부
    val minRequiredRating: Int,
    val pageCount: Int,
    val members: List<GroupMember>,
)

data class GroupMember(
    val userId: Long,
    val userNickName: String,
    val profileColor: String?,
    val lastPageRead: Int,     // 서버 0-based 유지
    val progressPercent: Int,
    val isHost: Boolean,
    val hasRated: Boolean,
    val profileImageUrl: String?,
    val ratedUserIds : List<Long>
)

fun GroupDetailResponse.toDomain(): GroupDetail = GroupDetail(
    roomTitle = roomTitle,
    category = category,
    schedule = schedule,
    memberCount = memberCount,
    maxMemberCount = maxMemberCount,
    description = description,
    isCompleted = allMembersCompleted,
    minRequiredRating = minRequiredRating,
    isHost = isHost,
    pageCount = pageCount,
    members = members.map { it.toDomain() }
)

fun GroupMemberDetailDto.toDomain(): GroupMember = GroupMember(
    userId = userId,
    userNickName = userNickName,
    profileColor = profileColor,
    lastPageRead = maxReadPage,
    progressPercent = progressPercent,
    isHost = isHost,
    hasRated = ratingSubmitted,   // ★ 서버 필드명은 isCompleted지만 의미는 "평가 완료"
    profileImageUrl = profileImageUrl,
    ratedUserIds = ratedUserIds

)