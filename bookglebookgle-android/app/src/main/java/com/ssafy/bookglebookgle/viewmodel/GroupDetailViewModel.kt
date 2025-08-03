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

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepositoryImpl: GroupRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

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
}