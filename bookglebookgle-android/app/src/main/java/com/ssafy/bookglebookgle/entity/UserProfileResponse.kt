package com.ssafy.bookglebookgle.entity

data class UserProfileResponse(
    val email: String,
    val nickname: String,
    val profileColor: String?,     // 예: "#AABBCC" (없을 수도)
    val avgRating: Float,          // 평균 평점
    val participatedGroups: Int,   // 총 참여 모임 수
    val completedGroups: Int,      // 완료 모임 수
    val incompleteGroups: Int,     // 미완료 모임 수
    val totalActiveHours: Int      // 총 활동 시간(시간)
)

data class UserProfile(
    val email: String,
    val nickname: String,
    val profileColor: String?,
    val avgRating: Float,
    val participatedGroups: Int,
    val completedGroups: Int,
    val incompleteGroups: Int,
    val totalActiveHours: Int
)

fun UserProfileResponse.toDomain() = UserProfile(
    email = email,
    nickname = nickname,
    profileColor = profileColor,
    avgRating = avgRating,
    participatedGroups = participatedGroups,
    completedGroups = completedGroups,
    incompleteGroups = incompleteGroups,
    totalActiveHours = totalActiveHours
)