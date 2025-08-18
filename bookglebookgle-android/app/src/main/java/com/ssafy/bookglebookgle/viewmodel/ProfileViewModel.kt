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
import kotlinx.coroutines.Job
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

    private val _saved = MutableSharedFlow<Unit>()
    val saved = _saved.asSharedFlow()

    // 진행 중인 로딩 작업을 추적하기 위한 Job
    private var loadProfileJob: Job? = null

    fun loadProfile() {
        // 이미 진행 중인 로딩 작업이 있으면 취소
        loadProfileJob?.cancel()

        _uiState.value = ProfileUiState.Loading
        loadProfileJob = viewModelScope.launch {
            try {
                val profile = getProfileUseCase()
                Log.d("ProfileVM", "Loaded profile: $profile")
                _uiState.value = ProfileUiState.Success(profile)
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.message ?: "프로필 로드 실패")
            }
        }
    }

    fun updateProfile(nickname: String?, colorHex: String?, imageUrl : String?) {
        // 이미 저장 중이면 중복 실행 방지
        if (_saving.value) {
            Log.d("ProfileVM", "Already saving, ignoring duplicate request")
            return
        }

        viewModelScope.launch {
            _saving.value = true
            _nicknameError.value = null
            try {
                updateProfileUseCase(nickname, colorHex, imageUrl)


                Log.d("ProfileVM", "updateProfile: 윤수")
                // 현재 상태가 Success인 경우에만 직접 업데이트하고, 그렇지 않으면 새로 로드
                val currentState = _uiState.value

                if (currentState is ProfileUiState.Success) {
                    // 성공 상태인 경우 현재 데이터를 업데이트
                    val updatedProfile = currentState.data.copy(
                        nickname = nickname ?: currentState.data.nickname,
                        profileColor = colorHex ?: currentState.data.profileColor,
                        profileImgUrl = imageUrl ?: currentState.data.profileImgUrl
                    )

                    _uiState.value = ProfileUiState.Success(updatedProfile)
                }

                _events.emit("프로필이 저장됐어요")

                // saved 이벤트는 맨 마지막에 emit
                _saved.emit(Unit)
            } catch (e: HttpException) {
                Log.e("ProfileVM", "HTTP error during profile update: ${e.code()}")
                if (e.code() == 409) {
                    _nicknameError.value = "이미 사용 중인 닉네임이에요."
                    _events.emit("닉네임이 이미 사용 중입니다. 다른 닉네임을 입력해주세요.")
                } else {
                    _events.emit("저장 실패(${e.code()}): ${e.message()}")
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Error during profile update: ${e.message}", e)
                _events.emit("저장 실패: ${e.message ?: "알 수 없는 오류"}")
            } finally {
                _saving.value = false
                Log.d("ProfileVM", "Profile update completed, saving = false")
            }
        }
    }

    private suspend fun loadProfileInternal() {
        Log.d("ProfileVM", "Starting loadProfileInternal...")
        val profile = getProfileUseCase()
        Log.d("ProfileVM", "Loaded profile: $profile")
        _uiState.value = ProfileUiState.Success(profile)
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                val response = logoutUseCase.deleteAccount()
                if (response.isSuccessful) {
                    _events.emit("계정이 삭제됐어요")
                    _logoutCompleted.value = true
                } else {
                    _events.emit("계정 삭제 실패: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Error deleting account: ${e.message}", e)
                _events.emit("계정 삭제 중 오류 발생: ${e.message ?: "알 수 없는 오류"}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _logoutCompleted.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadProfileJob?.cancel()
    }

}
