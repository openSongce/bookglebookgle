package com.ssafy.bookglebookgle.viewmodel

// LoginViewModel.kt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import com.ssafy.bookglebookgle.repository.LoginRepository
import com.ssafy.bookglebookgle.util.TokenManager
import javax.inject.Inject


@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    val id = mutableStateOf("")
    val password = mutableStateOf("")
    val loginSuccess = mutableStateOf<Boolean?>(null)
    val errorMessage = mutableStateOf<String?>(null)

    fun login() {
        viewModelScope.launch {
            try {
                val response = loginRepository.login(id.value, password.value)

                tokenManager.saveTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken
                )

                loginSuccess.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.value = "로그인에 실패했습니다: ${e.message}"
                loginSuccess.value = false
            }
        }
    }
}
