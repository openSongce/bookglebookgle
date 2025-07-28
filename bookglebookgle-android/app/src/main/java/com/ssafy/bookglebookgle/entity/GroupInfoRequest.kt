package com.ssafy.bookglebookgle.entity

data class GroupInfoRequest(
    val roomTitle: String,
    val description: String,
    val category: String,
    val schedule: String, // ISO 8601 format: 2025-08-05T19:30:00
    val groupMaxNum: Int,
    val readingMode: String = "FOLLOW",
    val minRequiredRating: Int = 4,
    val imageBased: Boolean = false
)