package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse

interface ChatRepository {
    suspend fun getChatList(): List<ChatListResponse>

    suspend fun getChatMessages(groupId: Long): ChatMessagesResponse
}