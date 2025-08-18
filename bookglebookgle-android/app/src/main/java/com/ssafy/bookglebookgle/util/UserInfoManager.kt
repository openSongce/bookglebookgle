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
        const val USER_ID = "user_id"
        const val EMAIL = "email"
        const val NICKNAME = "nickname"
        const val PROFILE_IMAGE = "profile_image"
        const val AVG_RATING = "average_rate"
        const val REVIEW_COUNT = "review_count"
        const val LOGIN_PROVIDER = "login_provider"
    }

    fun saveUserInfo(response: LoginResponse) {
        prefs.edit().apply {
            putString(EMAIL, response.email)
            putString(NICKNAME, response.nickname)
            putString(PROFILE_IMAGE, response.profileImageUrl)
            putFloat(AVG_RATING, response.avgRating)
            putInt(REVIEW_COUNT, response.reviewCount)
            putString(LOGIN_PROVIDER, response.loginProvider)
            putLong(USER_ID, response.userId)
            apply()
        }
    }

    fun getUserId() : Long = prefs.getLong(USER_ID, -1)
    fun getEmail(): String? = prefs.getString(EMAIL, null)
    fun getNickname(): String? = prefs.getString(NICKNAME, null)
    fun getProfileImage(): String? = prefs.getString(PROFILE_IMAGE, null)
    fun getAverageScore(): Float = prefs.getFloat(AVG_RATING, 0.0f)
    fun getReviewCount(): Int = prefs.getInt(REVIEW_COUNT, 0)
    fun getLoginProvider(): String? = prefs.getString(LOGIN_PROVIDER, null)

    fun clearUserInfo() {
        prefs.edit().apply {
            remove(USER_ID)
            remove(EMAIL)
            remove(NICKNAME)
            remove(PROFILE_IMAGE)
            remove(AVG_RATING)
            remove(REVIEW_COUNT)
            remove(LOGIN_PROVIDER)
            apply()
        }
    }
}
