package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.RegisterRequest
import com.ssafy.bookglebookgle.entity.SendCodeRequest
import com.ssafy.bookglebookgle.entity.VerifyCodeRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/check/email")
    suspend fun sendAuthCode(@Body request: SendCodeRequest): Response<Unit>

    @POST("auth/email/verify")
    suspend fun verifyAuthCode(@Body request: VerifyCodeRequest): Response<Boolean>

    @POST("auth/signup/email")
    suspend fun register(@Body request: RegisterRequest): Response<Unit>

    @GET("auth/check/nickname")
    suspend fun checkNickname(@Query("nickname") nickname: String): Response<Boolean>


}
