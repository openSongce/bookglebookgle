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
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withTimeoutOrNull
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
            try {
                val refresh = tokenManager.getRefreshToken()

                if (refresh.isNullOrBlank()) {
                    _uiState.value = UiState.GoLogin
                    return@launch
                }
                // 2 서버에 refresh 요청
                val newToken = withTimeoutOrNull(5000) {
                    loginRepository.refreshToken(refresh)
                }

                if (newToken != null) {
                    tokenManager.saveTokens(newToken.accessToken, newToken.refreshToken)
                    _uiState.value = UiState.GoMain //  자동 로그인 성공
                } else {  // 3 refreshToken 만료·위조 등
                    tokenManager.clearTokens()
                    _uiState.value = UiState.GoLogin
                }

            } catch (e: Exception) {
                Log.e("SplashViewModel", "자동로그인 error: ${e.message}")
                tokenManager.clearTokens()
                _uiState.value = UiState.GoLogin
            }
        }
    }

}
