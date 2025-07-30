package com.ssafy.bookglebookgle.usecase

import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import com.ssafy.bookglebookgle.util.UserInfoManager
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: LoginRepository,
    private val tokenManager: TokenManager,
    private val userInfoManager: UserInfoManager
) {
    suspend operator fun invoke(id: String, password: String): Boolean {
        val response = repository.login(id, password)
        tokenManager.saveTokens(response.accessToken, response.refreshToken)
        userInfoManager.saveUserInfo(response)
        return true
    }

}
