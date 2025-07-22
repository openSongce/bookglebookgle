package com.ssafy.bookglebookgle.network

import com.ssafy.bookglebookgle.util.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking { tokenManager.getAccessToken() }
        val newRequest = chain.request().newBuilder().apply {
            if (!accessToken.isNullOrEmpty()) {
                addHeader("Authorization", "Bearer $accessToken")
            }
        }.build()
        return chain.proceed(newRequest)
    }
}
