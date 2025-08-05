package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.ChatListResponse

interface ChatRepository {
    suspend fun getChatList(): List<ChatListResponse>
}