package com.ssafy.bookglebookgle.usecase

import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import javax.inject.Inject

class KakaoLoginUseCase @Inject constructor(
    private val repository: LoginRepository,
    private val tokenManager: TokenManager
) {
    suspend operator fun invoke(accessToken: String): Boolean {
        val response = repository.loginWithKakao(accessToken)
        tokenManager.saveTokens(response.accessToken, response.refreshToken)
        return true
    }
}
