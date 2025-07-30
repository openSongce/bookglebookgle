package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.usecase.LogoutUseCase
import com.ssafy.bookglebookgle.util.UserInfoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val nickname = userInfoManager.getNickname()
    val email = userInfoManager.getEmail()
    val profileImageUrl = userInfoManager.getProfileImage()
    val averageScore = userInfoManager.getAverageScore()
    val reviewCount = userInfoManager.getReviewCount()

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _logoutCompleted.value = true
        }
    }
}
