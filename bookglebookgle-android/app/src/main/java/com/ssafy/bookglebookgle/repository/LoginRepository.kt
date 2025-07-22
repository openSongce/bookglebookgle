package com.ssafy.bookglebookgle.repository

// LoginRepository.kt


import com.ssafy.bookglebookgle.network.LoginApi
import com.ssafy.bookglebookgle.entity.LoginRequest
import com.ssafy.bookglebookgle.entity.LoginResponse
import com.ssafy.bookglebookgle.entity.RefreshRequest
import javax.inject.Inject

class LoginRepository @Inject constructor(
    private val loginApi: LoginApi
) {
    suspend fun login(id: String, password: String): LoginResponse {
        val request = LoginRequest(id, password)
        return loginApi.login(request)
    }

    suspend fun refreshToken(refreshToken: String) =
        loginApi.refreshToken(RefreshRequest(refreshToken))

}

