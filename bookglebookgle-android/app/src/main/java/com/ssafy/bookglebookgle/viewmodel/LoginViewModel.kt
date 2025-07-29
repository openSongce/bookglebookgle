package com.ssafy.bookglebookgle.viewmodel

// LoginViewModel.kt

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import com.ssafy.bookglebookgle.usecase.GoogleLoginUseCase
import com.ssafy.bookglebookgle.usecase.KakaoLoginUseCase
import com.ssafy.bookglebookgle.usecase.LoginUseCase
import javax.inject.Inject


@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val googleLoginUseCase: GoogleLoginUseCase,
    private var kakaoLoginUseCase: KakaoLoginUseCase
) : ViewModel() {

    val id = mutableStateOf("")
    val password = mutableStateOf("")
    val loginSuccess = mutableStateOf<Boolean?>(null)
    val errorMessage = mutableStateOf<String?>(null)

    fun login() {
        viewModelScope.launch {
            try {
                val success = loginUseCase(id.value, password.value)
                loginSuccess.value = success


            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("login", "login: ${e.message}")
                errorMessage.value = "아이디 또는 비밀번호를 확인해주세요."
                loginSuccess.value = false
            }
        }
    }


    fun googleLogin(idToken: String) {
        viewModelScope.launch {
            val success = googleLoginUseCase(idToken)
            loginSuccess.value = success
        }
    }

    fun kakaoLogin(accessToken: String) {
        viewModelScope.launch {
            val success = kakaoLoginUseCase(accessToken)
            loginSuccess.value = success
        }
    }



}
