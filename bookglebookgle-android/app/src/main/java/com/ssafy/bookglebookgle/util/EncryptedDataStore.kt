package com.ssafy.bookglebookgle.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedDataStore @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "secure_prefs"
        private const val ACCESS_KEY = "access_token"
        private const val REFRESH_KEY = "refresh_token"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(ACCESS_KEY, accessToken)
            putString(REFRESH_KEY, refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String? {
        return prefs.getString(ACCESS_KEY, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(REFRESH_KEY, null)
    }

    fun clearTokens() {
        prefs.edit().apply {
            remove(ACCESS_KEY)
            remove(REFRESH_KEY)
            apply()
        }
    }

    fun getPrefs(): SharedPreferences = prefs
}
