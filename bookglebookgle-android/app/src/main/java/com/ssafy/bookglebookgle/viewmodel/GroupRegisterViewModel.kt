package com.ssafy.bookglebookgle.viewmodel

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PdfFile
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
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
    private val groupRepositoryImpl: GroupRepositoryImpl
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

    // 최소 요구 평점 추가
    var minRequiredRating by mutableStateOf(0)
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

    // 최소 요구 평점 업데이트 함수
    fun updateMinRequiredRating(rating: Int) {
        minRequiredRating = rating
    }

    // 입력 검증
    fun isFormValid(): Boolean {
        return groupName.isNotEmpty() &&
                selectedCategory.isNotEmpty() &&
                selectedDateTime.isNotEmpty() &&
                PdfFile != null
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
//        val parts = dateTime.split("-")
//        return if (parts.size == 4) {
//            "${parts[0]}-${parts[1]}-${parts[2]}T${parts[3]}:00"
//        } else {
//            throw IllegalArgumentException("Invalid dateTime format: $dateTime")
//        }

        return dateTime
    }

    // JSON 생성
    private fun createGroupInfoJson(isOcrRequired: Boolean): String {
        val json = JSONObject().apply {
            put("roomTitle", groupName)
            put("description", groupDescription)
            put("category", getCategoryForServer(selectedCategory))
            put("minRequiredRating", minRequiredRating)
            put("schedule", formatDateTimeForServer(selectedDateTime))
            put("groupMaxNum", maxMembers)
            put("readingMode", ReadingMode.FOLLOW.value)
            put("imageBased", if (isOcrRequired) "true" else "false")
        }
        return json.toString()
    }

    // PDF 파일을 MultipartBody.Part로 변환
    private fun createPdfMultipart(file: File): MultipartBody.Part {
        val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", file.name, requestFile)
    }

    /**
     * OCR 필요 여부에 따라 적절한 API를 호출하는 통합 메서드
     * @param isOcrRequired OCR이 필요한지 여부
     */
    fun createGroupWithPdf(
        isOcrRequired: Boolean
    ) {
        val currentPdfFile = pdfFile
        if (currentPdfFile == null || !currentPdfFile.exists()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "PDF 파일이 선택되지 않았습니다."
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val groupInfoJson = createGroupInfoJson(isOcrRequired)
                val groupInfoRequestBody = groupInfoJson.toRequestBody("application/json".toMediaTypeOrNull())
                val filePart = createPdfMultipart(currentPdfFile)

                Log.d(TAG, "Creating group - OCR Required: $isOcrRequired")
                Log.d(TAG, "Min Required Rating: $minRequiredRating")
                Log.d(TAG, "ImageBased field will be set to: ${if (isOcrRequired) "true" else "false"}")
                Log.d(TAG, "PDF File: ${currentPdfFile.name}, Size: ${currentPdfFile.length()} bytes")
                Log.d(TAG, "Group Info JSON: $groupInfoJson")

                val response = if (isOcrRequired) {
                    // OCR이 필요한 경우 (이미지 기반 PDF)
                    Log.d(TAG, "Calling createGroup API (with OCR)")
                    groupRepositoryImpl.createGroup(
                        groupInfo = groupInfoRequestBody,
                        file = filePart,
                    )
                } else {
                    // OCR이 불필요한 경우 (텍스트 추출 가능 PDF)
                    Log.d(TAG, "Calling createGroupWithoutOcr API")
                    groupRepositoryImpl.createGroupWithoutOcr(
                        groupInfo = groupInfoRequestBody,
                        file = filePart,
                    )
                }

                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                    Log.d(TAG, "Group created successfully")
                } else {
                    val errorBody = response.errorBody()?.string()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "그룹 생성에 실패했습니다. (${response.code()}) $errorBody"
                    )
                    Log.e(TAG, "Group creation failed: ${response.code()}, Error: $errorBody")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "네트워크 오류가 발생했습니다: ${e.message}"
                )
                Log.e(TAG, "Group creation error", e)
            }
        }
    }

    fun updatePdfFile(file: File) {
        pdfFile = file
        selectedPdfFileName = file.name
        Log.d(TAG, "PDF file updated: ${file.name}, Size: ${file.length()} bytes")
        Log.d(TAG, "Form valid after PDF update: ${isFormValid()}")
    }

    fun resetPdfFile() {
        pdfFile = null
        selectedPdfFileName = ""
        Log.d(TAG, "PDF file reset, Form valid: ${isFormValid()}")
    }

    // 그룹 생성 (OCR 포함)
    fun createGroup(pdfFile: File) {
        this.pdfFile = pdfFile
        createGroupWithPdf(isOcrRequired = true)
    }

    fun createGroupWithoutOcr(pdfFile: File) {
        this.pdfFile = pdfFile
        createGroupWithPdf(isOcrRequired = false)
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
        pdfFile = null
        minRequiredRating = 0
    }

    /**
     * 현재 선택된 PDF 파일 정보 로깅
     */
    fun logCurrentState() {
        Log.d(TAG, """
            현재 상태:
            - 그룹명: $groupName
            - 카테고리: $selectedCategory
            - 최대 인원: $maxMembers
            - 일정: $selectedDateTime
             최소 요구 평점: $minRequiredRating
            - PDF 파일: ${pdfFile?.name ?: "없음"}
            - 폼 유효성: ${isFormValid()}
        """.trimIndent())
    }
}