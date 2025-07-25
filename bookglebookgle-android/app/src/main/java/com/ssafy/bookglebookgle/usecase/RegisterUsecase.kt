package com.ssafy.bookglebookgle.usecase

import com.ssafy.bookglebookgle.entity.RegisterRequest
import com.ssafy.bookglebookgle.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend fun sendAuthCode(email: String): Boolean {
        return authRepository.sendAuthCode(email)
    }

    suspend fun verifyCode(email: String, code: String): Boolean {
        return authRepository.verifyCode(email, code)
    }

    suspend fun checkNicknameAvailable(nickname: String): Boolean {
        return authRepository.checkNickname(nickname)
    }

    suspend fun registerUser(email: String, nickname: String, password: String): Boolean {
        return authRepository.registerUser(RegisterRequest(email, nickname, password))
    }

}
