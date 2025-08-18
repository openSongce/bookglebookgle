package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.UserProfile

interface ProfileRepository {
    suspend fun fetchMyProfile(): UserProfile
    suspend fun updateProfile(nickname: String?, colorHex: String?, imageUrl: String?)
}