package com.ssafy.bookglebookgle.usecase

import android.util.Log
import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import com.ssafy.bookglebookgle.util.UserInfoManager
import javax.inject.Inject

class KakaoLoginUseCase @Inject constructor(
    private val repository: LoginRepository,
    private val tokenManager: TokenManager
) {
    suspend operator fun invoke(accessToken: String): Boolean {
        return try {
            val response = repository.loginWithKakao(accessToken)
            tokenManager.saveTokens(response.accessToken, response.refreshToken)
            true
        } catch (e: Exception) {
            Log.e("KAKAO_LOGIN", "카카오 로그인 실패", e)
            false
        }
    }

}
