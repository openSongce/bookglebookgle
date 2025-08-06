package com.ssafy.bookglebookgle.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.GroupListResponse
import com.ssafy.bookglebookgle.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _readingGroups = mutableStateOf<List<GroupListResponse>>(emptyList())
    val readingGroups: State<List<GroupListResponse>> = _readingGroups

    private val _studyGroups = mutableStateOf<List<GroupListResponse>>(emptyList())
    val studyGroups: State<List<GroupListResponse>> = _studyGroups

    private val _reviewGroups = mutableStateOf<List<GroupListResponse>>(emptyList())
    val reviewGroups: State<List<GroupListResponse>> = _reviewGroups

    private val _allGroups = mutableStateOf<List<GroupListResponse>>(emptyList())
    val allGroups: State<List<GroupListResponse>> = _allGroups

    // 검색 관련 상태
    private val _searchResults = mutableStateOf<List<GroupListResponse>>(emptyList())
    val searchResults: State<List<GroupListResponse>> = _searchResults

    private val _isSearching = mutableStateOf(false)
    val isSearching: State<Boolean> = _isSearching

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    init {
        getchAllGroups()
    }

    fun getchAllGroups() {
        viewModelScope.launch {
            try {
                val response = groupRepository.getAllGroups()
                if (response.isSuccessful) {
                    _allGroups.value = response.body() ?: emptyList()
                    response.body()?.let { groups ->
                        _readingGroups.value = groups.filter { it.category == "READING" }
                        _studyGroups.value = groups.filter { it.category == "STUDY" }
                        _reviewGroups.value = groups.filter { it.category == "REVIEW" }
                    }
                } else {
                    _errorMessage.value = "서버 에러: ${response.code()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "네트워크 에러: ${e.message}"
            }
        }
    }

    /**
     * 그룹 검색
     * @param query 검색할 방 제목
     */
    fun searchGroups(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchQuery.value = ""
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            _searchQuery.value = query
            _errorMessage.value = null

            try {
                val response = groupRepository.searchGroups(query.trim())
                if (response.isSuccessful) {
                    response.body()?.let { groups ->
                        _searchResults.value = groups
                    } ?: run {
                        _searchResults.value = emptyList()
                    }
                } else {
                    _errorMessage.value = "검색 실패: ${response.code()}"
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                _errorMessage.value = "검색 중 오류 발생: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * 검색 결과 초기화
     */
    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _searchQuery.value = ""
        _errorMessage.value = null
    }

    /**
     * 에러 메시지 초기화
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * 카테고리별 검색 결과 가져오기
     */
    fun getSearchResultsByCategory(category: String): List<GroupListResponse> {
        return _searchResults.value.filter { it.category == category }
    }

    /**
     * 검색 상태인지 확인
     */
    fun isInSearchMode(): Boolean {
        return _searchQuery.value.isNotEmpty()
    }
}
