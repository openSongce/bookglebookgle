package com.ssafy.bookglebookgle.util

import android.content.SharedPreferences
import com.ssafy.bookglebookgle.entity.LoginResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserInfoManager @Inject constructor(
    encryptedDataStore: EncryptedDataStore
) {
    private val prefs: SharedPreferences = encryptedDataStore.getPrefs()

    private companion object {
        const val EMAIL = "email"
        const val NICKNAME = "nickname"
        const val PROFILE_IMAGE = "profile_image"
        const val AVG_SCORE = "average_score"
        const val REVIEW_COUNT = "review_count"
        const val LOGIN_PROVIDER = "login_provider"
    }

    fun saveUserInfo(response: LoginResponse) {
        prefs.edit().apply {
            putString(EMAIL, response.email)
            putString(NICKNAME, response.nickname)
            putString(PROFILE_IMAGE, response.profileImageUrl)
            putFloat(AVG_SCORE, response.averageScore)
            putInt(REVIEW_COUNT, response.reviewCount)
            putString(LOGIN_PROVIDER, response.loginProvider)
            apply()
        }
    }

    fun getEmail(): String? = prefs.getString(EMAIL, null)
    fun getNickname(): String? = prefs.getString(NICKNAME, null)
    fun getProfileImage(): String? = prefs.getString(PROFILE_IMAGE, null)
    fun getAverageScore(): Float = prefs.getFloat(AVG_SCORE, 0.0f)
    fun getReviewCount(): Int = prefs.getInt(REVIEW_COUNT, 0)
    fun getLoginProvider(): String? = prefs.getString(LOGIN_PROVIDER, null)

    fun clearUserInfo() {
        prefs.edit().apply {
            remove(EMAIL)
            remove(NICKNAME)
            remove(PROFILE_IMAGE)
            remove(AVG_SCORE)
            remove(REVIEW_COUNT)
            remove(LOGIN_PROVIDER)
            apply()
        }
    }
}
