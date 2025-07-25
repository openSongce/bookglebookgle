package com.ssafy.bookglebookgle.util


import android.util.Base64
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val encryptedDataStore: EncryptedDataStore
) {
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



    // === EncryptedDataStore 연동 ===
    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedDataStore.saveTokens(accessToken, refreshToken)
    }

    fun getAccessToken(): String? {
        return encryptedDataStore.getAccessToken()
    }

    fun getRefreshToken(): String? {
        return encryptedDataStore.getRefreshToken()
    }

    fun clearTokens() {
        encryptedDataStore.clearTokens()
    }

}
