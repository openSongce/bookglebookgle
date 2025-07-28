package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.entity.RefreshRequest
import com.ssafy.bookglebookgle.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

    sealed class UiState {
        data object Loading : UiState()
        data object GoMain  : UiState()
        data object GoLogin : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    fun autoLogin() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            try {
                Log.d("SplashViewModel", "자동 로그인 시작")

                val refresh = tokenManager.getRefreshToken()
                val access = tokenManager.getAccessToken()

                Log.d("SplashViewModel", "저장된 accessToken = $access")
                Log.d("SplashViewModel", "저장된 refreshToken = $refresh")

                val result = if (refresh.isNullOrBlank()) {
                    Log.d("SplashViewModel", "refreshToken 없음 -> 로그인 화면으로")
                    UiState.GoLogin
                } else {
                    Log.d("SplashViewModel", "refresh 요청 시도 중...")

                    val newToken = withTimeoutOrNull(5000) {
                        loginRepository.refreshToken(refresh)
                    }

                    Log.d("SplashViewModel", "refresh 결과 = ${newToken?.accessToken}")

                    if (newToken != null) {
                        tokenManager.saveTokens(newToken.accessToken, newToken.refreshToken)
                        Log.d("SplashViewModel", "자동 로그인 성공 -> 메인 화면으로")
                        UiState.GoMain
                    } else {
                        Log.d("SplashViewModel", "refresh 실패 -> 로그인 화면으로")
                        tokenManager.clearTokens()
                        UiState.GoLogin
                    }
                }

                // 최소 2초 보장
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = 500 - elapsedTime

                if (remainingTime > 0) {
                    delay(remainingTime)
                }

                _uiState.value = result

            } catch (e: Exception) {

                // 예외 발생 시에도 최소 2초 유지
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = 500 - elapsedTime

                if (remainingTime > 0) {
                    delay(remainingTime)
                }

                tokenManager.clearTokens()
                _uiState.value = UiState.GoLogin
            }
        }
    }

}
