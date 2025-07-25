package com.ssafy.bookglebookgle.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.ssafy.bookglebookgle.entity.RegisterStep
import com.ssafy.bookglebookgle.ui.screen.isValidEmail
import com.ssafy.bookglebookgle.usecase.RegisterUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    var step by mutableStateOf(RegisterStep.EMAIL)
        private set

    var registerSuccess by mutableStateOf(false)
        private set


    var email by mutableStateOf("")
    var authCode by mutableStateOf("")
    var isAuthFieldVisible by mutableStateOf(false)

    var nickname by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var errorMessage by mutableStateOf<String?>(null)

    var isRequestButtonEnabled by mutableStateOf(true)
    private var resendTimerJob: Job? = null

    var isNicknameValid by mutableStateOf(false)
    var isNicknameChecking by mutableStateOf(false)


    fun onEmailChange(value: String) {
        email = value
    }

    fun onAuthCodeChange(value: String) {
        authCode = value
    }

    fun onNicknameChange(value: String) {
        nickname = value
        isNicknameValid = false
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

            isRequestButtonEnabled = false // 버튼 비활성화

            val success = registerUseCase.sendAuthCode(email)

            if (success) {
                errorMessage = null
                isAuthFieldVisible = true
                startResendTimer()
            } else {
                errorMessage = "이미 가입된 이메일입니다." // 중복된 경우 메시지
                isRequestButtonEnabled = true // 실패했을 경우 다시 활성화
            }
        }
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            delay(60000) // 60초
            isRequestButtonEnabled = true
        }
    }


    fun onNextOrSubmit() {
        when (step) {
            RegisterStep.EMAIL -> {
                viewModelScope.launch {
                    val valid = registerUseCase.verifyCode(email, authCode)
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
                if (!isNicknameValid) {
                    errorMessage = "닉네임 중복 확인이 필요합니다."
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
                    val success = registerUseCase.registerUser(email, nickname, password)
                    if (success) {
                        errorMessage = null
                        registerSuccess = true
                    } else {
                        errorMessage = "회원가입에 실패했습니다."
                    }
                }
            }
        }
    }

    fun onCheckNickname() {
        viewModelScope.launch {
            isNicknameChecking = true
            val available = registerUseCase.checkNicknameAvailable(nickname)
            isNicknameChecking = false

            if (available) {
                isNicknameValid = true
                errorMessage = null
            } else {
                isNicknameValid = false
                errorMessage = "이미 사용 중인 닉네임입니다."
            }
        }
    }

}
