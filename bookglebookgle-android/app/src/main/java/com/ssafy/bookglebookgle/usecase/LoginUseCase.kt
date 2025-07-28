package com.ssafy.bookglebookgle.usecase

import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: LoginRepository,
    private val tokenManager: TokenManager
) {
    suspend operator fun invoke(id: String, password: String): Boolean {
        val response = repository.login(id, password)
        tokenManager.saveTokens(response.accessToken, response.refreshToken)
        return true
    }

}
