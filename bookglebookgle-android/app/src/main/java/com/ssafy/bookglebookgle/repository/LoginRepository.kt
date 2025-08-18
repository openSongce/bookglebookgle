package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.*
import retrofit2.Response

interface LoginRepository {
    suspend fun login(id: String, password: String): LoginResponse
    suspend fun refreshToken(refreshToken: String): LoginResponse
    suspend fun loginWithGoogle(idToken: String): LoginResponse
    suspend fun loginWithKakao(accessToken: String): LoginResponse
    suspend fun logout(refreshToken: String)
    suspend fun deleteAccount(): Response<Unit>
}
