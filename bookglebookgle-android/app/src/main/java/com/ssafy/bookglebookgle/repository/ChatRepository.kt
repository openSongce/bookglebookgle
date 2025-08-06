package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse

interface ChatRepository {
    suspend fun getChatList(): List<ChatListResponse>

    suspend fun getChatMessages(roomId: Long, beforeId: Long, size: Int = 20): List<ChatMessagesResponse>

    suspend fun getLatestChatMessages(
        roomId: Long,
        size: Int
    ): List<ChatMessagesResponse>

    suspend fun getOlderChatMessages(
        roomId: Long,
        beforeMessageId: Long,
        size: Int
    ): List<ChatMessagesResponse>
}