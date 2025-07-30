package com.ssafy.bookglebookgle.entity

import com.ssafy.bookglebookgle.ui.screen.Group
import com.ssafy.bookglebookgle.ui.screen.GroupCategory

data class GroupListResponse(
    val groupId: Int,
    val roomTitle: String,
    val description: String,
    val category: String,
    val groupMaxNum: Int,
    val currentNum: Int,
    val minimumRating: Int
)

fun GroupListResponse.toDomain(): Group {
    return Group(
        id = groupId.toString(),
        title = roomTitle,
        description = description,
        category = when (category) {
            "READING" -> GroupCategory.READING
            "STUDY" -> GroupCategory.STUDY
            else -> GroupCategory.REVIEW
        },
        currentMembers = currentNum,
        maxMembers = groupMaxNum
    )
}
