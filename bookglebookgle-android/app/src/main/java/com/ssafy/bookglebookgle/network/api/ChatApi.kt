package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {

    @GET("chat/rooms")
    suspend fun getChatList(): Response<List<ChatListResponse>>

    @GET("chat/{roomId}/messages")
    suspend fun getChatMessages(
        @Path("roomId") roomId: Long,
        @Query("beforeId") beforeId: Long? = null,
        @Query("size") size: Int = 20
    ): Response<List<ChatMessagesResponse>>
}