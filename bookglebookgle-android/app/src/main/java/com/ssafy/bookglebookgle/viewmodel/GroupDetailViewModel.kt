package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "싸피_GroupDetailViewModel"

sealed class GroupDetailUiState {
    object Loading : GroupDetailUiState()
    data class Success(val groupDetail: GroupDetailResponse) : GroupDetailUiState()
    data class Error(val message: String) : GroupDetailUiState()
}

sealed class JoinGroupUiState {
    object Idle : JoinGroupUiState()
    object Loading : JoinGroupUiState()
    object Success : JoinGroupUiState()
    data class Error(val message: String) : JoinGroupUiState()
}

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepositoryImpl: GroupRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private val _joinGroupState = MutableStateFlow<JoinGroupUiState>(JoinGroupUiState.Idle)
    val joinGroupState: StateFlow<JoinGroupUiState> = _joinGroupState.asStateFlow()

    // 현재 그룹의 내가 참여한 상태를 관리
    private val _isMyGroup = MutableStateFlow(false)
    val isMyGroup: StateFlow<Boolean> = _isMyGroup.asStateFlow()

    /**
     * 초기 상태 설정
     */
    fun setInitialMyGroupState(isMyGroup: Boolean) {
        _isMyGroup.value = isMyGroup
        Log.d(TAG, "초기 내 그룹 상태 설정: $isMyGroup")
    }

    /**
     * 그룹 상세 정보 조회
     */
    fun getGroupDetail(groupId: Long) {
        Log.d(TAG, "그룹 상세 조회 시작 - groupId: $groupId")

        viewModelScope.launch {
            _uiState.value = GroupDetailUiState.Loading

            try {
                val response = groupRepositoryImpl.getGroupDetail(groupId)

                Log.d(TAG, "그룹 상세 조회 응답 - 성공여부: ${response.isSuccessful}, 코드: ${response.code()}")

                if (response.isSuccessful) {
                    response.body()?.let { groupDetail ->
                        Log.d(TAG, "그룹 상세 조회 성공 - 제목: ${groupDetail.roomTitle}, 카테고리: ${groupDetail.category}")
                        _uiState.value = GroupDetailUiState.Success(groupDetail)
                    } ?: run {
                        _uiState.value = GroupDetailUiState.Error("그룹 상세 정보를 가져올 수 없습니다.")
                    }
                } else {
                    Log.e(TAG, "그룹 상세 조회 실패 - 코드: ${response.code()}, 메시지: ${response.message()}")
                    _uiState.value = GroupDetailUiState.Error(
                        "그룹 상세 조회 실패: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "그룹 상세 조회 중 예외 발생: ${e.message}", e)
                _uiState.value = GroupDetailUiState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }
        }
    }

    /**
     * 그룹 참여
     */
    fun joinGroup(groupId: Long) {
        Log.d(TAG, "그룹 참여 시작 - groupId: $groupId")

        viewModelScope.launch {
            _joinGroupState.value = JoinGroupUiState.Loading

            try {
                val response = groupRepositoryImpl.joinGroup(groupId)

                Log.d(TAG, "그룹 참여 응답 - 성공여부: ${response.isSuccessful}, 코드: ${response.code()}")

                if (response.isSuccessful) {
                    Log.d(TAG, "그룹 참여 성공 - 코드: ${response.code()}")
                    _joinGroupState.value = JoinGroupUiState.Success

                    // 가입 성공 시 내 그룹 상태를 true로 변경
                    _isMyGroup.value = true

                    // 그룹 상세 정보를 다시 조회하여 최신 정보 반영 (멤버 수 등)
                    getGroupDetail(groupId)

                } else {
                    Log.e(TAG, "그룹 참여 실패 - 코드: ${response.code()}, 메시지: ${response.message()}")
                    _joinGroupState.value = JoinGroupUiState.Error(
                        "그룹 참여 실패: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "그룹 참여 중 예외 발생: ${e.message}", e)
                _joinGroupState.value = JoinGroupUiState.Error(
                    e.message ?: "그룹 참여 중 오류가 발생했습니다."
                )
            }
        }
    }

    /**
     * 모임 삭제
     * */
    fun deleteGroup(groupId: Long) {
        Log.d(TAG, "그룹 삭제 시작 - groupId: $groupId")

        viewModelScope.launch {
            try {
                val response = groupRepositoryImpl.deleteGroup(groupId)

                Log.d(TAG, "그룹 삭제 응답 - 성공여부: ${response.isSuccessful}, 코드: ${response.code()}")

                if (response.isSuccessful) {
                    Log.d(TAG, "그룹 삭제 성공 - 코드: ${response.code()}")
                    _isMyGroup.value = false
                } else {
                    Log.e(TAG, "그룹 삭제 실패 - 코드: ${response.code()}, 메시지: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "그룹 삭제 중 예외 발생: ${e.message}", e)
            }
        }
    }

    /**
     * 가입 상태 초기화
     */
    fun resetJoinGroupState() {
        _joinGroupState.value = JoinGroupUiState.Idle
    }
}