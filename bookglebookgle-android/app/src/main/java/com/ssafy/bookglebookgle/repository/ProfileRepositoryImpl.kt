package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.UserProfile
import com.ssafy.bookglebookgle.entity.UserProfileUpdateRequest
import com.ssafy.bookglebookgle.entity.toDomain
import com.ssafy.bookglebookgle.network.api.ProfileApi
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val api: ProfileApi
): ProfileRepository {
    override suspend fun fetchMyProfile(): UserProfile = api.getMyProfile().toDomain()

    override suspend fun updateProfile(nickname: String?, colorHex: String?, imageUrl: String?) {
        val normalized = colorHex?.let { normalizeHex(it) } // "#xxxxxx" & uppercase
        api.updateProfile(
            UserProfileUpdateRequest(nickname = nickname, profileColor = normalized, profileImgUrl = imageUrl)
        )
    }

    private fun normalizeHex(raw: String): String {
        val t = raw.trim().uppercase()
        return if (t.startsWith("#")) t else "#$t"
    }
}