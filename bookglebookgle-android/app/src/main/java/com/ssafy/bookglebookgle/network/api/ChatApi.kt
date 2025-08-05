package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ChatApi {

    @GET("chat/rooms")
    suspend fun getChatList(): Response<List<ChatListResponse>>

    @GET("chat/{groupId}/messages")
    suspend fun getChatMessages(
        @Path("groupId") groupId: Long
    ): Response<ChatMessagesResponse>
}