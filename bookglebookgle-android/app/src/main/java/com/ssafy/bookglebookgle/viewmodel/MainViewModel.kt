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
}
