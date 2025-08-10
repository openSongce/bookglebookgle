package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.UserProfile
import com.ssafy.bookglebookgle.usecase.GetProfileUseCase
import com.ssafy.bookglebookgle.usecase.LogoutUseCase
import com.ssafy.bookglebookgle.usecase.UpdateProfileUseCase
import com.ssafy.bookglebookgle.util.UserInfoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject


sealed interface ProfileUiState {
    object Loading : ProfileUiState
    data class Success(val data: UserProfile) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val userInfoManager: UserInfoManager,
    private val updateProfileUseCase: UpdateProfileUseCase
) : ViewModel() {

    private val _logoutCompleted = MutableStateFlow(false)
    val logoutCompleted: StateFlow<Boolean> = _logoutCompleted

    private val _userId = MutableStateFlow(userInfoManager.getUserId())
    val userId: StateFlow<Long> = _userId.asStateFlow()

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _events = MutableSharedFlow<String>()           // 스낵바/토스트 메시지
    val events = _events.asSharedFlow()

    private val _nicknameError = MutableStateFlow<String?>(null)
    val nicknameError: StateFlow<String?> = _nicknameError.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()   //
    val saved = _saved.asSharedFlow()

    fun loadProfile() {
        _uiState.value = ProfileUiState.Loading
        viewModelScope.launch {
            try {
                val profile = getProfileUseCase()
                Log.d("ProfileVM", "Loaded profile: $profile")
                _uiState.value = ProfileUiState.Success(profile)
                // 필요하면 UserInfoManager에 캐시 업데이트 (메서드 있으면 사용)
                // userInfoManager.updateNickname(profile.nickname) ...
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "프로필 로드 실패")
            }
        }
    }

    fun updateProfile(nickname: String?, colorHex: String?) {
        viewModelScope.launch {
            _saving.value = true
            _nicknameError.value = null
            try {
                updateProfileUseCase(nickname, colorHex)
                _events.emit("프로필이 저장됐어요")
                _saved.emit(Unit)
                loadProfile()
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    _nicknameError.value = "이미 사용 중인 닉네임이에요."
                    _events.emit("닉네임이 이미 사용 중입니다. 다른 닉네임을 입력해주세요.")
                } else {
                    _events.emit("저장 실패(${e.code()}): ${e.message()}")
                }
            } catch (e: Exception) {
                _events.emit("저장 실패: ${e.message ?: "알 수 없는 오류"}")
            } finally {
                _saving.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _logoutCompleted.value = true
        }
    }
}
