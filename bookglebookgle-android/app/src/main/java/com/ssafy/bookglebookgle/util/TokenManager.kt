package com.ssafy.bookglebookgle.util

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }

    // === JWT 디코딩 ===
    private fun getPayload(token: String): JSONObject? {
        return try {
            val payload = token.split(".")[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            JSONObject(decodedString)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserInfo(token: String): JSONObject? {
        return getPayload(token)?.optJSONObject("userInfo")
    }

    fun getExpireTime(token: String): Long {
        return getPayload(token)?.optLong("exp", 0L) ?: 0L
    }

    fun isTokenExpired(token: String): Boolean {
        val exp = getExpireTime(token)
        val currentTime = System.currentTimeMillis() / 1000
        return currentTime >= exp
    }

    // === DataStore 관련 ===
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    suspend fun getAccessToken(): String? {
        return context.dataStore.data
            .map { prefs -> prefs[ACCESS_TOKEN_KEY] }
            .first()
    }

    suspend fun getRefreshToken(): String? {
        return context.dataStore.data
            .map { prefs -> prefs[REFRESH_TOKEN_KEY] }
            .first()
    }

    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
        }
    }

    // === 토큰 자동 갱신 판단 ===
    suspend fun shouldRefreshToken(): Boolean {
        val token = getAccessToken()
        return token?.let { isTokenExpired(it) } ?: true
    }
}
