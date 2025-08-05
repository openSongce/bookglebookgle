package com.ssafy.bookglebookgle.entity

// 채팅 메시지 목록 응답 (실시간 채팅용)
data class ChatMessagesResponse(
    val groupId: Long,
    val messages: List<ChatMessage>
)

// 개별 채팅 메시지
data class ChatMessage(
    val messageId: Long,
    val userId: Long,
    val nickname: String,
    val profileImage: String?,
    val message: String,
    val timestamp: String,
)