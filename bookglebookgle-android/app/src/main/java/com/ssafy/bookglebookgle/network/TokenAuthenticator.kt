package com.ssafy.bookglebookgle.network

import com.ssafy.bookglebookgle.entity.RefreshRequest
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

        return try {
            val newToken = runBlocking {
                loginApi.refreshToken(RefreshRequest(refreshToken))
            }
            runBlocking {
                tokenManager.saveTokens(newToken.accessToken, newToken.refreshToken)
            }

            response.request().newBuilder()
                .header("Authorization", "Bearer ${newToken.accessToken}")
                .build()
        } catch (e: Exception) {
            null
        }
    }
}