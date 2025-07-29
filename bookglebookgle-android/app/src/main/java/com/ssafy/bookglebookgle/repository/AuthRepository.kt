package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.RegisterRequest

interface AuthRepository {
    suspend fun sendAuthCode(email: String): Boolean
    suspend fun verifyCode(email: String, code: String): Boolean
    suspend fun registerUser(request: RegisterRequest): Boolean
    suspend fun checkNickname(nickname: String): Boolean
}
