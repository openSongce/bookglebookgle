package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.*
import com.ssafy.bookglebookgle.network.api.LoginApi
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val loginApi: LoginApi
) : LoginRepository {

    override suspend fun login(id: String, password: String): LoginResponse {
        val request = LoginRequest(id, password)
        return loginApi.login(request)
    }

    override suspend fun refreshToken(refreshToken: String): LoginResponse {
        return loginApi.refreshToken(RefreshRequest(refreshToken))
    }

    override suspend fun loginWithGoogle(idToken: String): LoginResponse {
        return loginApi.googleLogin(GoogleLoginRequest(idToken))
    }

    override suspend fun loginWithKakao(accessToken: String): LoginResponse {
        return loginApi.kakaoLogin(KakaoLoginRequest(accessToken))
    }

    override suspend fun logout(refreshToken: String) {
        loginApi.logout(LogoutRequest(refreshToken))
        loginApi.unregisterFcmToken(LogoutRequest(refreshToken))
    }
}
