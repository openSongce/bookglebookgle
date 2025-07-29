package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.GoogleLoginRequest
import com.ssafy.bookglebookgle.entity.KakaoLoginRequest
import com.ssafy.bookglebookgle.entity.LoginRequest
import com.ssafy.bookglebookgle.entity.LoginResponse
import com.ssafy.bookglebookgle.entity.LogoutRequest
import com.ssafy.bookglebookgle.entity.RefreshRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface LoginApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): LoginResponse

    @POST("auth/oauth/google")
    suspend fun googleLogin(@Body token: GoogleLoginRequest): LoginResponse

    @POST("auth/oauth/kakao")
    suspend fun kakaoLogin(@Body token: KakaoLoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest)
}
