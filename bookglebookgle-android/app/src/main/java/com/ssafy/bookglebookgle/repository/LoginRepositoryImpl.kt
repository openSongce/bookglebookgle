package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.*
import com.ssafy.bookglebookgle.network.api.LoginApi
import com.ssafy.bookglebookgle.repository.fcm.FcmRepository
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val loginApi: LoginApi,
    private val fcmRepository: FcmRepository
) : LoginRepository {

    override suspend fun login(id: String, password: String): LoginResponse {
        val request = LoginRequest(id, password)
        val res = loginApi.login(request)
        fcmRepository.registerTokenAsync(token = null, uidFallback = res.userId)
        return res
    }

    override suspend fun refreshToken(refreshToken: String): LoginResponse {
        return loginApi.refreshToken(RefreshRequest(refreshToken))
    }

    override suspend fun loginWithGoogle(idToken: String): LoginResponse {
        val res = loginApi.googleLogin(GoogleLoginRequest(idToken))
        fcmRepository.registerTokenAsync(token = null, uidFallback = res.userId)
        return res
    }

    override suspend fun loginWithKakao(accessToken: String): LoginResponse {
        val res = loginApi.kakaoLogin(KakaoLoginRequest(accessToken))
        fcmRepository.registerTokenAsync(token = null, uidFallback = res.userId)
        return res
    }

    override suspend fun logout(refreshToken: String) {
        loginApi.logout(LogoutRequest(refreshToken))
        loginApi.unregisterFcmToken(LogoutRequest(refreshToken))
    }
}
