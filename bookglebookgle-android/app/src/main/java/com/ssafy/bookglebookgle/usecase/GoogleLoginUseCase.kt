package com.ssafy.bookglebookgle.usecase

import android.util.Log
import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import javax.inject.Inject

class GoogleLoginUseCase @Inject constructor(
    private val repository: LoginRepository,
    private val tokenManager: TokenManager
) {
    suspend operator fun invoke(idToken: String): Boolean {
        return try {
            val result = repository.loginWithGoogle(idToken)
            tokenManager.saveTokens(result.accessToken, result.refreshToken)
            val storedAccess = tokenManager.getAccessToken()
            val storedRefresh = tokenManager.getRefreshToken()
            Log.d("Login", "저장된 accessToken: $storedAccess")
            Log.d("Login", "저장된 refreshToken: $storedRefresh")
            true
        } catch (e: Exception) {
            false
        }
    }
}
