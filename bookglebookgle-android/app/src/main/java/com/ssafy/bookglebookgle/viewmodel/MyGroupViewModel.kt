package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.MyGroupResponse
import com.ssafy.bookglebookgle.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "싸피_MyGroupViewModel"

data class MyGroupUiState(
    val isLoading: Boolean = false,
    val groups: List<MyGroupResponse> = emptyList()
)

@HiltViewModel
class MyGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyGroupUiState())
    val uiState: StateFlow<MyGroupUiState> = _uiState.asStateFlow()

    init {
        loadMyGroups()
    }

    /**
     * 내 모임 목록 조회
     */
    fun loadMyGroups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val response = groupRepository.getMyGroups()

                if (response.isSuccessful) {
                    val groups = response.body() ?: emptyList()
                    Log.d(TAG, "내 모임 조회 성공 - 모임 수: ${groups.size}")

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        groups = groups
                    )
                } else {
                    Log.e(TAG, "내 모임 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        groups = emptyList()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "내 모임 조회 네트워크 오류: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    groups = emptyList()
                )
            }
        }
    }

    /**
     * 새로고침
     */
    fun refresh() {
        Log.d(TAG, "내 모임 목록 새로고침 요청")
        loadMyGroups()
    }
}