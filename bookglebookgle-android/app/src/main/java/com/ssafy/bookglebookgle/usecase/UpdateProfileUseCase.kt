package com.ssafy.bookglebookgle.usecase

import com.ssafy.bookglebookgle.repository.ProfileRepository
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val repo: ProfileRepository
) {
    suspend operator fun invoke(nickname: String?, colorHex: String?, imageUrl: String?) =
        repo.updateProfile(nickname, colorHex, imageUrl)
}