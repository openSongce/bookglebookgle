package com.ssafy.bookglebookgle.entity

data class ChatListResponse(
    val groupId: Long,
    val groupTitle: String,
    val imageUrl : String?,
    val category: String?,
    val lastMessage: String?,
    val lastMessageTime: String?,
    val memberCount: Int,
    val unreadCount: Int = 0
)