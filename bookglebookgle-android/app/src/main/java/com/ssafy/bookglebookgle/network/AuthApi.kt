package com.ssafy.bookglebookgle.network

import com.ssafy.bookglebookgle.entity.RegisterRequest
import com.ssafy.bookglebookgle.entity.SendCodeRequest
import com.ssafy.bookglebookgle.entity.VerifyCodeRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/auth/send-code")
    suspend fun sendAuthCode(@Body request: SendCodeRequest): Response<Unit>

    @POST("/api/auth/verify-code")
    suspend fun verifyAuthCode(@Body request: VerifyCodeRequest): Response<Unit>

    @POST("/api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Unit>

}
