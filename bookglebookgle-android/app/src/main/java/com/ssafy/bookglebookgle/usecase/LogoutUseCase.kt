package com.ssafy.bookglebookgle.usecase

import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: LoginRepository,
    private val tokenManager: TokenManager
) {
    suspend operator fun invoke() {
        val refreshToken = tokenManager.getRefreshToken() ?: return

        // 서버에 로그아웃 요청
        runCatching {
            repository.logout(refreshToken)
        }

        // 토큰 삭제 (성공/실패 상관없이)
        tokenManager.clearTokens()
    }
}
