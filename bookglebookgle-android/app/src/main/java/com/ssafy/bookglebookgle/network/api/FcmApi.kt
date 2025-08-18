package com.ssafy.bookglebookgle.network.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Query

data class FcmTokenRequest(val token: String)

interface FcmApi {
    // 서버가 인증 필요하면 Authorization 헤더가 붙음(기존 Interceptor)
    // 인증 없이 테스트하려면 uidFallback 전달 가능(서버가 허용 시)
    @PUT("/fcm/token")
    suspend fun registerToken(
        @Body body: FcmTokenRequest,
        @Query("uidFallback") uidFallback: Long? = null
    ): Response<Unit>
}
