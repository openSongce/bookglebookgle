package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.UserProfileResponse
import com.ssafy.bookglebookgle.entity.UserProfileUpdateRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface ProfileApi {
    @GET("/users/profile")
    suspend fun getMyProfile(): UserProfileResponse

    @PUT("/users/profile")
    suspend fun updateProfile(@Body body: UserProfileUpdateRequest): UserProfileResponse
}