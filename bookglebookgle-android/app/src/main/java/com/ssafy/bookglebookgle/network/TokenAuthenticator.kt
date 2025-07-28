package com.ssafy.bookglebookgle.network

import android.util.Log
import com.ssafy.bookglebookgle.entity.RefreshRequest
import com.ssafy.bookglebookgle.network.api.LoginApi
import com.ssafy.bookglebookgle.util.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val loginApiProvider: Provider<LoginApi>
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val loginApi = loginApiProvider.get()
        val refreshToken = runBlocking { tokenManager.getRefreshToken() } ?: return null

        Log.d("JWT", "⚠️ accessToken 만료 감지. refreshToken = $refreshToken")

        return try {
            val newToken = runBlocking {
                loginApi.refreshToken(RefreshRequest(refreshToken))
            }


            tokenManager.saveTokens(newToken.accessToken, newToken.refreshToken)

            response.request.newBuilder()
                .header("Authorization", "Bearer ${newToken.accessToken}")
                .build()
        } catch (e: Exception) {
            null
        }
    }
}