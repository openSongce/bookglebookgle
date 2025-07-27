package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf.response.PdfNoteListModel
import com.ssafy.bookglebookgle.repository.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PdfUploadViewModel @Inject constructor(
    private val pdfRepository: PdfRepository
) : ViewModel() {

    private val _uploading = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val uploading: StateFlow<Map<Long, Boolean>> = _uploading

    private val _uploadMessage = MutableStateFlow<String?>(null)
    val uploadMessage: StateFlow<String?> = _uploadMessage

    fun uploadPdf(pdf: PdfNoteListModel, localFile: File) {
        val pdfId = pdf.id
        _uploading.value = _uploading.value + (pdfId to true)

        viewModelScope.launch {
            val response = pdfRepository.uploadPdf(localFile)
            if (response != null) {
                _uploadMessage.value = "PDF 업로드 완료 (id: ${response.pdfId})"
            } else {
                _uploadMessage.value = "PDF 업로드 실패"
            }

            _uploading.value = _uploading.value - pdfId

            // 메시지 잠시 후 제거
            kotlinx.coroutines.delay(2000)
            _uploadMessage.value = null
        }
    }

    fun setUploadMessage(message: String) {
        _uploadMessage.value = message
    }
}
