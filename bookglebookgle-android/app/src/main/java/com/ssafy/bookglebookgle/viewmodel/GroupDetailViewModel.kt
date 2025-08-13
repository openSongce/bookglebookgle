package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ssafy.bookglebookgle.entity.GroupDetail
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import com.ssafy.bookglebookgle.ui.component.GroupEditData
import com.ssafy.bookglebookgle.util.UserInfoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

private const val TAG = "싸피_GroupDetailViewModel"

sealed class GroupDetailUiState {
    object Loading : GroupDetailUiState()
    data class Success(val groupDetail: GroupDetail) : GroupDetailUiState() // ← 여기
    data class Error(val message: String) : GroupDetailUiState()
}
sealed class RateMemberUiState {
    object Idle : RateMemberUiState()
    object Loading : RateMemberUiState()
    object Success : RateMemberUiState()
    data class Error(val message: String) : RateMemberUiState()
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
    private val groupRepositoryImpl: GroupRepositoryImpl,
    private val userInfoManager: UserInfoManager
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

    // 내 userId 저장
    private val _currentUserId = MutableStateFlow<Long?>(null)
    fun setCurrentUserId(id: Long) {
        _currentUserId.value = id
        recomputeHasRatedByMe() // lastDetail이 있으면 즉시 재계산
    }
    val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()

    private val _rateMemberState = MutableStateFlow<RateMemberUiState>(RateMemberUiState.Idle)
    val rateMemberState: StateFlow<RateMemberUiState> = _rateMemberState.asStateFlow()



    // 모임 종료 여부 (상단 isCompleted)
    private val _groupCompleted = MutableStateFlow(false)
    val groupCompleted: StateFlow<Boolean> = _groupCompleted.asStateFlow()

    // 내가 평점을 이미 제출했는지 (members[i].isCompleted)
    private val _hasRatedByMe = MutableStateFlow(false)
    val hasRatedByMe: StateFlow<Boolean> = _hasRatedByMe.asStateFlow()

    // 종료됐고 + 나는 아직 제출 안했으면 평점 버튼 활성화
    val canRate: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(_groupCompleted, _hasRatedByMe) { completed, rated ->
            completed && !rated
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    // 편의: 마지막 응답 보관(유저id 나중에 들어와도 재계산)
    private var lastDetail: GroupDetail? = null

    // 편의 변환: PDF 쪽에서 쓰기 쉽게 가공
    data class InitialMember(
        val userId: String,
        val nickname: String,
        val color: String?,
        val imageUrl: String? = null,
        val isHost: Boolean,
        val maxReadPage1Based: Int // 0-based -> 1-based 변환
    )

    fun GroupDetail.toInitialMembers(): List<InitialMember> =
        members.map {
            InitialMember(
                userId = it.userId.toString(),
                nickname = it.userNickName,     // 도메인 필드명이 다르면 여기를 맞춰주세요
                color = it.profileColor,
                imageUrl = it.photoUrl,
                isHost = it.isHost,
                maxReadPage1Based = (it.lastPageRead + 1).coerceAtLeast(0)
            )
        }

    fun GroupDetail.toInitialProgressMap(): Map<String, Int> =
        members.associate { it.userId.toString() to (it.lastPageRead + 1).coerceAtLeast(0) }

    init {
        userInfoManager.getUserId()?.let { setCurrentUserId(it) }
    }



    /**
     * 초기 상태 설정
     */
    fun setInitialMyGroupState(isMyGroup: Boolean) {
        if (_uiState.value is GroupDetailUiState.Loading) {
            _isMyGroup.value = isMyGroup
            Log.d(TAG, "초기 상태 설정 (서버 데이터 로드 전): $isMyGroup")
        } else {
            Log.d(TAG, "서버 데이터가 이미 있어서 초기 상태 설정 무시")
        }
    }

    /**
     * 그룹 상세 정보 조회
     */
    fun getGroupDetail(groupId: Long) {
        viewModelScope.launch {
            _uiState.value = GroupDetailUiState.Loading
            try {
                // repo는 바로 GroupDetail을 던짐 (실패시 예외 throw)
                val detail: GroupDetail = groupRepositoryImpl.getGroupDetail(groupId)

                lastDetail = detail
                _groupCompleted.value = detail.isCompleted

                val me = _currentUserId.value
                _hasRatedByMe.value = if (me != null) {
                    detail.members.firstOrNull { it.userId == me }?.hasRated == true
                } else {
                    false
                }

                val currentUserId = _currentUserId.value
                val isUserInGroup = if (currentUserId != null) {
                    detail.members.any { it.userId == currentUserId }
                } else {
                    false
                }


                _isMyGroup.value = isUserInGroup
                Log.d(TAG, "서버 데이터 기준 isMyGroup 설정: $isUserInGroup (userId: $currentUserId)")
                _uiState.value = GroupDetailUiState.Success(detail)
            } catch (e: Exception) {
                _uiState.value = GroupDetailUiState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }
        }
    }


    fun recomputeHasRatedByMe() {
        val body = lastDetail ?: return
        val me = _currentUserId.value ?: return
        _hasRatedByMe.value = body.members.firstOrNull { it.userId == me }?.hasRated == true
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
                    _isMyGroup.value = true
                    _joinGroupState.value = JoinGroupUiState.Success
//                    getGroupDetail(groupId)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "그룹 참여 실패 - 코드: ${response.code()}, 메시지: $errorBody")

                    // 서버 응답 코드와 현재 그룹 상태를 기반으로 에러 메시지 결정
                    val errorMessage = when (response.code()) {
                        400 -> {
                            // errorBody 내용을 우선적으로 체크
                            when {
                                !errorBody.isNullOrBlank() -> {
                                    when {
                                        errorBody.contains("이미 참가한 그룹") -> "이미 참가한 그룹입니다."
                                        errorBody.contains("그룹 정원이 초과되었습니다") ||
                                                errorBody.contains("정원이 초과") ||
                                                errorBody.contains("정원") && errorBody.contains("초과") -> "그룹 정원이 초과되었습니다."
                                        errorBody.contains("평점이 낮아") ||
                                                errorBody.contains("평점") && errorBody.contains("참가할 수 없습니다") ||
                                                errorBody.contains("최소 요구 평점") ||
                                                errorBody.contains("평점이 부족") -> {
                                            // 현재 그룹 정보에서 최소 요구 평점 가져오기
                                            val currentState = _uiState.value
                                            if (currentState is GroupDetailUiState.Success) {
                                                val requiredRating = currentState.groupDetail.minRequiredRating
                                                "평점이 낮아 가입할 수 없습니다."
                                            } else {
                                                "평점이 낮아 가입할 수 없습니다."
                                            }
                                        }
                                        else -> errorBody
                                    }
                                }
                                else -> {
                                    // errorBody가 비어있을 때는 현재 그룹 상태를 확인해서 추론
                                    val currentState = _uiState.value
                                    if (currentState is GroupDetailUiState.Success) {
                                        val groupDetail = currentState.groupDetail
                                        when {
                                            groupDetail.memberCount >= groupDetail.maxMemberCount -> {
                                                Log.e(TAG, "정원 초과로 추정됨 - 현재: ${groupDetail.memberCount}/${groupDetail.maxMemberCount}")
                                                "그룹 정원이 초과되었습니다"
                                            }
                                            else -> "평점이 낮아 가입할 수 없습니다."
                                        }
                                    } else {
                                        "가입 요청이 거부되었습니다"
                                    }
                                }
                            }
                        }
                        404 -> "해당 그룹이 존재하지 않습니다."
                        500 -> "서버 오류가 발생했습니다."
                        else -> errorBody ?: "그룹 참여에 실패했습니다."
                    }

                    Log.e(TAG, "최종 에러 메시지: $errorMessage")
                    _joinGroupState.value = JoinGroupUiState.Error(errorMessage)
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

    fun rateMember(groupId: Long, toId: Long, score: Float) {
        viewModelScope.launch {
            _rateMemberState.value = RateMemberUiState.Loading
            try {
                val resp = groupRepositoryImpl.rateMember(groupId, toId, score)
                if (resp.isSuccessful) {
                    _rateMemberState.value = RateMemberUiState.Success
                    // 최신화: 내 제출여부/멤버 상태 갱신
                    getGroupDetail(groupId)
                } else {
                    val body = resp.errorBody()?.string().orEmpty()
                    _rateMemberState.value = RateMemberUiState.Error(
                        when (resp.code()) {
                            400 -> when {
                                body.contains("본인") || body.contains("자기") -> "본인은 평가할 수 없어요."
                                body.contains("중복") -> "이미 해당 멤버를 평가했어요."
                                else -> body.ifBlank { "잘못된 요청입니다." }
                            }
                            404 -> "그룹 또는 대상을 찾지 못했어요."
                            500 -> "서버 오류가 발생했어요."
                            else -> body.ifBlank { "평가 등록에 실패했어요." }
                        }
                    )
                }
            } catch (e: Exception) {
                _rateMemberState.value = RateMemberUiState.Error(e.message ?: "네트워크 오류가 발생했어요.")
            }
        }
    }

    fun resetRateMemberState() { _rateMemberState.value = RateMemberUiState.Idle }

}