package com.ssafy.bookglebookgle.entity

data class UserProfileResponse(
    val email: String,
    val nickname: String,
    val profileColor: String?,     // 예: "#AABBCC" (없을 수도)
    val avgRating: Float,          // 평균 평점
    val participatedGroups: Int,   // 총 참여 모임 수
    val completedGroups: Int,      // 완료 모임 수
    val incompleteGroups: Int,     // 미완료 모임 수
    val totalActiveHours: Int,      // 총 활동 시간(시간)

    val totalActiveSeconds: Long = 0L,   // 기본값 넣어두면 구버전 서버에도 안전
    val prettyActiveTime: String? = null // null/빈문자 대비해서 도메인에서 보정

)

data class UserProfile(
    val email: String,
    val nickname: String,
    val profileColor: String?,
    val avgRating: Float,
    val participatedGroups: Int,
    val completedGroups: Int,
    val incompleteGroups: Int,
    val totalActiveHours: Int,
    val totalActiveSeconds: Long,
    val prettyActiveTime: String
)

fun UserProfileResponse.toDomain(): UserProfile {
    val hoursFromSeconds = (totalActiveSeconds / 3600).toInt()
    val hoursSafe = if (totalActiveHours > 0) totalActiveHours else hoursFromSeconds

    val prettySafe = prettyActiveTime
        ?.takeIf { it.isNotBlank() }
        ?: totalActiveSeconds.toPretty()

    return UserProfile(
        email = email,
        nickname = nickname,
        profileColor = profileColor,
        avgRating = avgRating,
        participatedGroups = participatedGroups,
        completedGroups = completedGroups,
        incompleteGroups = incompleteGroups,
        totalActiveSeconds = totalActiveSeconds,
        totalActiveHours = hoursSafe,
        prettyActiveTime = prettySafe
    )
}

private fun Long.toPretty(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    return when {
        h <= 0L && m <= 0L -> "0분"
        h <= 0L -> "${m}분"
        m <= 0L -> "${h}시간"
        else -> String.format("%d시간 %02d분", h, m)
    }
}
