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
    val type: MessageType = MessageType.NORMAL,
    val aiResponse: String? = null,
    val suggestedTopics: List<String> = emptyList()
)

enum class MessageType(val value: String) {
    NORMAL("NORMAL"),
    AI_RESPONSE("AI_RESPONSE"),
    DISCUSSION_START("DISCUSSION_START"),
    DISCUSSION_END("DISCUSSION_END");

    companion object {
        fun fromString(value: String): MessageType {
            return entries.find { it.value == value } ?: NORMAL
        }
    }
}