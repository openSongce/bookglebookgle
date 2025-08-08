package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.usecase.LogoutUseCase
import com.ssafy.bookglebookgle.util.UserInfoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val userInfoManager: UserInfoManager // ✅ 추가
) : ViewModel() {

    private val _logoutCompleted = MutableStateFlow(false)
    val logoutCompleted: StateFlow<Boolean> = _logoutCompleted

    // 사용자 정보 상태
    private val _nickname = MutableStateFlow(userInfoManager.getNickname())
    val nickname: StateFlow<String?> = _nickname.asStateFlow()

    private val _email = MutableStateFlow(userInfoManager.getEmail())
    val email: StateFlow<String?> = _email.asStateFlow()

    private val _profileImageUrl = MutableStateFlow(userInfoManager.getProfileImage())
    val profileImageUrl: StateFlow<String?> = _profileImageUrl.asStateFlow()

    private val _averageScore = MutableStateFlow(userInfoManager.getAverageScore())
    val averageScore: StateFlow<Float> = _averageScore.asStateFlow()

    private val _reviewCount = MutableStateFlow(userInfoManager.getReviewCount())
    val reviewCount: StateFlow<Int> = _reviewCount.asStateFlow()

    private val _userId = MutableStateFlow(userInfoManager.getUserId())
    val userId: StateFlow<Long> = _userId.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            userInfoManager.clearUserInfo()
            _logoutCompleted.value = true
        }
    }

    fun refreshUserInfo() {
        _nickname.value = userInfoManager.getNickname()
        _email.value = userInfoManager.getEmail()
        _profileImageUrl.value = userInfoManager.getProfileImage()
        _averageScore.value = userInfoManager.getAverageScore()
        _reviewCount.value = userInfoManager.getReviewCount()
        _userId.value = userInfoManager.getUserId()
    }
}
