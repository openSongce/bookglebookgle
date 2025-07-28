package com.ssafy.bookglebookgle.viewmodel

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

private const val TAG = "싸피_GroupRegisterViewModel"
data class GroupRegisterUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

enum class GroupCategory(val value: String) {
    READING("READING"),
    STUDY("STUDY"),
    CORRECTION("CORRECTION")
}

enum class ReadingMode(val value: String) {
    FOLLOW("FOLLOW"),
    FREE("FREE")
}

@HiltViewModel
class GroupRegisterViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupRegisterUiState())
    val uiState: StateFlow<GroupRegisterUiState> = _uiState.asStateFlow()

    var pdfFile: File? = null

    // UI 상태 변수들
    var selectedCategory by mutableStateOf("")
        private set

    var maxMembers by mutableStateOf(1)
        private set

    var selectedDateTime by mutableStateOf("")
        private set

    var groupName by mutableStateOf("")
        private set

    var groupDescription by mutableStateOf("")
        private set

    var showDateTimePicker by mutableStateOf(false)
        private set

    var selectedPdfUri by mutableStateOf<Uri?>(null)
        private set

    var selectedPdfFileName by mutableStateOf("")
        private set

    // UI 업데이트 함수들
    fun updateCategory(category: String) {
        selectedCategory = if (selectedCategory == category) "" else category
    }

    fun updateMaxMembers(members: Int) {
        maxMembers = members
    }

    fun updateDateTime(dateTime: String) {
        selectedDateTime = dateTime
    }

    fun updateGroupName(name: String) {
        groupName = name
    }

    fun updateGroupDescription(description: String) {
        groupDescription = description
    }

    fun showDateTimePicker() {
        showDateTimePicker = true
    }

    fun hideDateTimePicker() {
        showDateTimePicker = false
    }

    fun updatePdfFile(uri: Uri, fileName: String) {
        selectedPdfUri = uri
        selectedPdfFileName = fileName
    }

    // 입력 검증
    fun isFormValid(): Boolean {
        return groupName.isNotEmpty() &&
                selectedCategory.isNotEmpty() &&
                selectedDateTime.isNotEmpty() &&
                selectedPdfUri != null
    }

    // 카테고리를 서버 형식으로 변환
    private fun getCategoryForServer(category: String): String {
        return when (category) {
            "독서" -> GroupCategory.READING.value
            "학습" -> GroupCategory.STUDY.value
            "첨삭" -> GroupCategory.CORRECTION.value
            else -> GroupCategory.STUDY.value
        }
    }

    // 날짜 형식 변환
    private fun formatDateTimeForServer(dateTime: String): String {
        val parts = dateTime.split("-")
        return if (parts.size == 4) {
            "${parts[0]}-${parts[1]}-${parts[2]}T${parts[3]}:00"
        } else {
            throw IllegalArgumentException("Invalid dateTime format: $dateTime")
        }
    }

    // JSON 생성
    private fun createGroupInfoJson(): String {
        val json = JSONObject().apply {
            put("roomTitle", groupName)
            put("description", groupDescription)
            put("category", getCategoryForServer(selectedCategory))
            put("schedule", formatDateTimeForServer(selectedDateTime))
            put("groupMaxNum", maxMembers)
            put("readingMode", ReadingMode.FOLLOW.value)
            put("minRequiredRating", 4)
            put("imageBased", true)
        }
        return json.toString()
    }

    // PDF 파일을 MultipartBody.Part로 변환
    private fun createPdfMultipart(file: File): MultipartBody.Part {
        val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", file.name, requestFile)
    }

    // 그룹 생성 (OCR 포함)
    fun createGroup(pdfFile: File) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val groupInfoJson = createGroupInfoJson()
                val groupInfoRequestBody = groupInfoJson.toRequestBody("application/json".toMediaTypeOrNull())
                val filePart = createPdfMultipart(pdfFile)

                Log.d(TAG, "Creating group with OCR: $groupInfoJson")

                val response = groupRepository.createGroup(
                    groupInfo = groupInfoRequestBody,
                    file = filePart,
                )

                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                    Log.d(TAG, "Group created successfully")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "그룹 생성에 실패했습니다. (${response.code()})"
                    )
                    Log.d(TAG, "Group creation failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "네트워크 오류가 발생했습니다: ${e.message}"
                )
                Log.d(TAG, "Group creation error", e)
            }
        }
    }

    // 그룹 생성 (OCR 없음)
    fun createGroupWithoutOcr(pdfFile: File) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val groupInfoJson = createGroupInfoJson()
                val groupInfoRequestBody = groupInfoJson.toRequestBody("application/json".toMediaTypeOrNull())
                val filePart = createPdfMultipart(pdfFile)

                Log.d(TAG, "Creating group without OCR: $groupInfoJson")

                val response = groupRepository.createGroupWithoutOcr(
                    groupInfo = groupInfoRequestBody,
                    file = filePart,
                )

                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                    Log.d(TAG, "Group created successfully without OCR")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "그룹 생성에 실패했습니다. (${response.code()})"
                    )
                    Log.d(TAG, "Group creation failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "네트워크 오류가 발생했습니다: ${e.message}"
                )
                Log.d(TAG, "Group creation error", e)
            }
        }
    }

    // 상태 초기화
    fun resetState() {
        _uiState.value = GroupRegisterUiState()
    }

    // 폼 초기화
    fun resetForm() {
        selectedCategory = ""
        maxMembers = 1
        selectedDateTime = ""
        groupName = ""
        groupDescription = ""
        showDateTimePicker = false
        selectedPdfUri = null
        selectedPdfFileName = ""
    }
}