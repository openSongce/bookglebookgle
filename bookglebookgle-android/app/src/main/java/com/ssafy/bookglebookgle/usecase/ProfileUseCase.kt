package com.ssafy.bookglebookgle.usecase

import com.ssafy.bookglebookgle.entity.UserProfile
import com.ssafy.bookglebookgle.repository.ProfileRepositoryImpl
import javax.inject.Inject

class GetProfileUseCase @Inject constructor(
    private val repo: ProfileRepositoryImpl
) {
    suspend operator fun invoke(): UserProfile = repo.fetchMyProfile()
}