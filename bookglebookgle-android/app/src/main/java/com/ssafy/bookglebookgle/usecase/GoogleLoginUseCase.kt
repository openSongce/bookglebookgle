package com.ssafy.bookglebookgle.usecase

import android.util.Log
import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import com.ssafy.bookglebookgle.util.UserInfoManager
import javax.inject.Inject

class GoogleLoginUseCase @Inject constructor(
    private val repository: LoginRepository,
    private val tokenManager: TokenManager,
    private val userInfoManager: UserInfoManager
) {
    suspend operator fun invoke(idToken: String): Boolean {
        return try {
            val response = repository.loginWithGoogle(idToken)
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            val storedAccess = tokenManager.getAccessToken()
            val storedRefresh = tokenManager.getRefreshToken()
            Log.d("Login", "저장된 accessToken: $storedAccess")
            Log.d("Login", "저장된 refreshToken: $storedRefresh")
            userInfoManager.saveUserInfo(response)
            true
        } catch (e: Exception) {
            false
        }
    }
}
