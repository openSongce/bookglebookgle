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

enum class GroupFilter(val displayName: String) {
    ALL("전체"),
    READING("독서"),
    STUDY("학습"),
    REVIEW("첨삭"),
    MY_CREATED("내가 생성한 모임")
}

data class MyGroupUiState(
    val isLoading: Boolean = false,
    val allGroups: List<MyGroupResponse> = emptyList(), // 전체 그룹 데이터
    val filteredGroups: List<MyGroupResponse> = emptyList(), // 필터링된 그룹 데이터
    val currentFilter: GroupFilter = GroupFilter.ALL,
    val isFilterDropdownVisible: Boolean = false
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
                        allGroups = groups,
                        filteredGroups = filterGroups(groups, _uiState.value.currentFilter)
                    )
                } else {
                    Log.e(TAG, "내 모임 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allGroups = emptyList(),
                        filteredGroups = emptyList()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "내 모임 조회 네트워크 오류: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allGroups = emptyList(),
                    filteredGroups = emptyList()
                )
            }
        }
    }

    /**
     * 필터 초기화 (전체로 리셋)
     */
    fun resetFilter() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            currentFilter = GroupFilter.ALL,
            filteredGroups = currentState.allGroups,
            isFilterDropdownVisible = false
        )
        Log.d(TAG, "필터 초기화 - 전체 모임 표시")
    }

    /**
     * 필터 변경
     */
    fun setFilter(filter: GroupFilter) {
        val currentState = _uiState.value
        val filteredGroups = filterGroups(currentState.allGroups, filter)

        _uiState.value = currentState.copy(
            currentFilter = filter,
            filteredGroups = filteredGroups,
            isFilterDropdownVisible = false
        )

        Log.d(TAG, "필터 변경: ${filter.displayName}, 필터링된 모임 수: ${filteredGroups.size}")
    }

    /**
     * 드롭다운 가시성 토글
     */
    fun toggleFilterDropdown() {
        _uiState.value = _uiState.value.copy(
            isFilterDropdownVisible = !_uiState.value.isFilterDropdownVisible
        )
    }

    /**
     * 드롭다운 숨기기
     */
    fun hideFilterDropdown() {
        _uiState.value = _uiState.value.copy(
            isFilterDropdownVisible = false
        )
    }

    /**
     * 그룹 필터링 로직
     */
    private fun filterGroups(groups: List<MyGroupResponse>, filter: GroupFilter): List<MyGroupResponse> {
        return when (filter) {
            GroupFilter.ALL -> groups
            GroupFilter.READING -> groups.filter { it.category.uppercase() == "READING" || it.category == "독서" }
            GroupFilter.STUDY -> groups.filter { it.category.uppercase() == "STUDY" || it.category == "학습" }
            GroupFilter.REVIEW -> groups.filter { it.category.uppercase() == "REVIEW" || it.category == "첨삭" }
            GroupFilter.MY_CREATED -> groups.filter { it.isHost == true }
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