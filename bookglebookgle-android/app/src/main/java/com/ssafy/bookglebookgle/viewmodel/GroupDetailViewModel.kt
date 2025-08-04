package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import com.ssafy.bookglebookgle.ui.component.GroupEditData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
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

// 모임 수정 상태
sealed class EditGroupUiState {
    object Idle : EditGroupUiState()
    object Loading : EditGroupUiState()
    object Success : EditGroupUiState()
    data class Error(val message: String) : EditGroupUiState()
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

    // 모임 수정 상태
    private val _editGroupState = MutableStateFlow<EditGroupUiState>(EditGroupUiState.Idle)
    val editGroupState: StateFlow<EditGroupUiState> = _editGroupState.asStateFlow()

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
     * 모임 수정
     * @param groupId 그룹 ID
     * @param editData 수정할 데이터
     */
    fun updateGroup(groupId: Long, editData: GroupEditData) {
        viewModelScope.launch {
            try {
                _editGroupState.value = EditGroupUiState.Loading
                Log.d(TAG, "모임 수정 시작 - groupId: $groupId")
                Log.d(TAG, "입력 데이터: $editData")

                // 모든 필드가 non-null이므로 직접 사용
                val groupUpdateRequest = mapOf(
                    "roomTitle" to editData.roomTitle,
                    "description" to editData.description,
                    "category" to editData.category,
                    "minRequiredRating" to editData.minRequiredRating,
                    "schedule" to editData.schedule,
                    "groupMaxNum" to editData.maxMemberCount
                )

                // 데이터 유효성 검사 추가
                if (editData.roomTitle.isBlank()) {
                    _editGroupState.value = EditGroupUiState.Error("모임 제목을 입력해주세요.")
                    return@launch
                }

                if (editData.maxMemberCount < 1) {
                    _editGroupState.value = EditGroupUiState.Error("최대 인원은 1명 이상이어야 합니다.")
                    return@launch
                }

                if (editData.minRequiredRating < 0 || editData.minRequiredRating > 5) {
                    _editGroupState.value = EditGroupUiState.Error("최소 요구 평점은 0~5 사이여야 합니다.")
                    return@launch
                }

                val gson = Gson()
                val jsonString = gson.toJson(groupUpdateRequest)
                Log.d(TAG, "전송할 JSON 데이터: $jsonString")

                val requestBody = jsonString.toRequestBody("application/json".toMediaType())
                val response = groupRepositoryImpl.editGroup(groupId, requestBody)

                if (response.isSuccessful) {
                    Log.d(TAG, "모임 수정 성공! 응답코드: ${response.code()}")
                    _editGroupState.value = EditGroupUiState.Success
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류"
                    Log.e(TAG, "모임 수정 실패 - 응답코드: ${response.code()}")
                    Log.e(TAG, "에러 메시지: $errorMessage")
                    _editGroupState.value = EditGroupUiState.Error("모임 수정에 실패했습니다: $errorMessage")
                }

            } catch (e: Exception) {
                Log.e(TAG, "모임 수정 중 예외 발생", e)
                _editGroupState.value = EditGroupUiState.Error("모임 수정 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    /**
     * 모임 탈퇴
     */
    fun leaveGroup(groupId: Long) {
        Log.d(TAG, "그룹 탈퇴 시작 - groupId: $groupId")

        viewModelScope.launch {
            try {
                val response = groupRepositoryImpl.leaveGroup(groupId)

                Log.d(TAG, "그룹 탈퇴 응답 - 성공여부: ${response.isSuccessful}, 코드: ${response.code()}")

                if (response.isSuccessful) {
                    Log.d(TAG, "그룹 탈퇴 성공 - 코드: ${response.code()}")
                    _isMyGroup.value = false
                } else {
                    Log.e(TAG, "그룹 탈퇴 실패 - 코드: ${response.code()}, 메시지: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "그룹 탈퇴 중 예외 발생: ${e.message}", e)
            }
        }
    }

    /**
     * 모임 수정 상태 초기화
     */
    fun resetEditGroupState() {
        _editGroupState.value = EditGroupUiState.Idle
    }

    /**
     * 가입 상태 초기화
     */
    fun resetJoinGroupState() {
        _joinGroupState.value = JoinGroupUiState.Idle
    }
}