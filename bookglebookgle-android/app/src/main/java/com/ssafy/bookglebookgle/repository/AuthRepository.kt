package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.RegisterRequest
import com.ssafy.bookglebookgle.entity.SendCodeRequest
import com.ssafy.bookglebookgle.entity.VerifyCodeRequest
import com.ssafy.bookglebookgle.network.api.AuthApi
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val authApi: AuthApi
) {
    suspend fun sendAuthCode(email: String): Boolean {
        return try {
            authApi.sendAuthCode(SendCodeRequest(email))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun verifyCode(email: String, code: String): Boolean {
        return try {
            authApi.verifyAuthCode(VerifyCodeRequest(email, code))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun registerUser(request: RegisterRequest): Boolean {
        return try {
            val response = authApi.register(request)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkNickname(nickname: String): Boolean {
        return try {
            authApi.checkNickname(nickname)
            true
        } catch (e: Exception) {
            false
        }
    }

}
