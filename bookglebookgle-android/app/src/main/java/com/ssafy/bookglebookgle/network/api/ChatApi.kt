package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.ChatListResponse
import retrofit2.Response
import retrofit2.http.GET

interface ChatApi {

    @GET("chat/list")
    suspend fun getChatList(): Response<List<ChatListResponse>>
}