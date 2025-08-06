package com.ssafy.bookglebookgle.entity

// REST API 채팅 메시지
data class ChatMessagesResponse(
    val id: Long,
    val userId: Long,
    val userNickname: String,
    val message: String,
    val createdAt: String,
)

// gRPC 채팅 메시지
data class ChatMessage(
    val messageId: Long,
    val userId: Long,
    val nickname: String,
    val profileImage: String?,
    val message: String,
    val timestamp: String,
)