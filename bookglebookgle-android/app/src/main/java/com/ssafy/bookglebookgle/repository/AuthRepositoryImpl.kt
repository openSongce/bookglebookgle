package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.entity.RegisterRequest
import com.ssafy.bookglebookgle.entity.SendCodeRequest
import com.ssafy.bookglebookgle.entity.VerifyCodeRequest
import com.ssafy.bookglebookgle.network.api.AuthApi
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi
) : AuthRepository {

    override suspend fun sendAuthCode(email: String): Boolean {
        return try {
            val response = authApi.sendAuthCode(SendCodeRequest(email))
            if (response.isSuccessful) {
                true
            } else {
                Log.e("Auth", "중복 이메일: ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            false
        }
    }


    override suspend fun verifyCode(email: String, code: String): Boolean {
        return try {
            val response = authApi.verifyAuthCode(VerifyCodeRequest(email, code))
            response.isSuccessful && (response.body() == true)
        } catch (e: Exception) {
            false
        }
    }


    override suspend fun registerUser(request: RegisterRequest): Boolean {
        return try {
            val response = authApi.register(request)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun checkNickname(nickname: String): Boolean {
        return try {
            val response = authApi.checkNickname(nickname)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

}
