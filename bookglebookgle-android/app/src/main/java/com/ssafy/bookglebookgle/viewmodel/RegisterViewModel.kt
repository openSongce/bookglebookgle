package com.ssafy.bookglebookgle.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.ssafy.bookglebookgle.entity.RegisterStep
import com.ssafy.bookglebookgle.repository.AuthRepository
import com.ssafy.bookglebookgle.ui.screen.isValidEmail
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var step by mutableStateOf(RegisterStep.EMAIL)
        private set

    var email by mutableStateOf("")
    var authCode by mutableStateOf("")
    var isAuthFieldVisible by mutableStateOf(false)

    var nickname by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var errorMessage by mutableStateOf<String?>(null)

    fun onEmailChange(value: String) {
        email = value
    }

    fun onAuthCodeChange(value: String) {
        authCode = value
    }

    fun onNicknameChange(value: String) {
        nickname = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun onConfirmPasswordChange(value: String) {
        confirmPassword = value
    }


    fun onRequestAuthCode() {
        viewModelScope.launch {
            if (!isValidEmail(email)) {
                errorMessage = "올바른 이메일 형식이 아닙니다."
                return@launch
            }
            val success = authRepository.sendAuthCode(email)
            if (success) {
                isAuthFieldVisible = true
            } else {
                errorMessage = "인증코드 전송에 실패했습니다."
            }
        }
    }

    fun onNextOrSubmit() {
        when (step) {
            RegisterStep.EMAIL -> {
                viewModelScope.launch {
                    val valid = authRepository.verifyCode(email, authCode)
                    if (valid) {
                        step = RegisterStep.DETAILS
                        errorMessage = null
                    } else {
                        errorMessage = "인증코드가 올바르지 않습니다."
                    }
                }
            }

            RegisterStep.DETAILS -> {
                if (nickname.isBlank()) {
                    errorMessage = "닉네임을 입력해주세요."
                    return
                }
                if (password != confirmPassword) {
                    errorMessage = "비밀번호가 일치하지 않습니다."
                    return
                }
                if (password.length < 6) {
                    errorMessage = "비밀번호는 6자리 이상이어야 합니다."
                    return
                }

                viewModelScope.launch {
                    val request = RegisterRequest(email, nickname, password)
                    val success = authRepository.registerUser(request)
                    if (success) {
                        // TODO: 회원가입 완료 후 이동 처리
                        errorMessage = null
                        // 예시: navController.navigate("login") 등
                    } else {
                        errorMessage = "회원가입에 실패했습니다."
                    }
                }


            }
        }
    }
}
