package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.entity.RefreshRequest
import com.ssafy.bookglebookgle.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val loginRepository: LoginRepository      // refresh API 호출용
) : ViewModel() {

    sealed class UiState { data object Loading : UiState()
        data object GoMain  : UiState()
        data object GoLogin : UiState() }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    fun autoLogin() {
        viewModelScope.launch {
            val refresh = tokenManager.getRefreshToken()

            if (refresh.isNullOrBlank()) {          // 1 refreshToken 자체가 없음
                _uiState.value = UiState.GoLogin
                return@launch
            }

            try {                                   // 2 서버에 refresh 요청
                val newToken = loginRepository.refreshToken(
                    refresh)
                tokenManager.saveTokens(newToken.accessToken, newToken.refreshToken)
                _uiState.value = UiState.GoMain      //  자동 로그인 성공

            } catch (e: Exception) {                // 3 refreshToken 만료·위조 등
                tokenManager.clearTokens()
                _uiState.value = UiState.GoLogin
            }
        }
    }
}
