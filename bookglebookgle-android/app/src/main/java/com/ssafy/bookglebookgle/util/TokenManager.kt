package com.ssafy.bookglebookgle.util


import android.util.Base64
import android.util.Log
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

    fun getUserInfo(token: String): String? {
        return getPayload(token)?.optString("sub")
    }



    // === EncryptedDataStore 연동 ===
    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedDataStore.saveTokens(accessToken, refreshToken)
    }

    fun getAccessToken(): String? {
//        return encryptedDataStore.getAccessToken()
        val token = encryptedDataStore.getAccessToken()
        val payload = getPayload(token ?: return null)
        val exp = payload?.optLong("exp")
        val iat = payload?.optLong("iat")
        Log.d("Problem", "🕒 accessToken iat=$iat, exp=$exp, now=${System.currentTimeMillis() / 1000}")
        return token
    }

    fun getRefreshToken(): String? {
        return encryptedDataStore.getRefreshToken()
    }

    fun clearTokens() {
        encryptedDataStore.clearTokens()
    }

}
