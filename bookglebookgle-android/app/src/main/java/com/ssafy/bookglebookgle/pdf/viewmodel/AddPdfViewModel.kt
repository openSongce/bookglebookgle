package com.ssafy.bookglebookgle.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf.exception.ValidationErrorException
import com.ssafy.bookglebookgle.pdf.response.TagModel
import com.ssafy.bookglebookgle.pdf.repository.PDFRepository
import com.ssafy.bookglebookgle.pdf.tools.FileDownloadTool
import com.ssafy.bookglebookgle.pdf.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AddPdfViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {
    var pdfFile: File? = null
    var selectedTag : TagModel? = null

    var pdfAddResponse = OperationsStateHandler(viewModelScope)


    private fun validation(title: String?, filePath: String?) {
        if (title.isNullOrEmpty()) {
            throw ValidationErrorException(1, "Please enter title of the book")
        }
        if (filePath.isNullOrEmpty()) {
            throw ValidationErrorException(2, "Please select or download pdf first.")
        }
    }

    fun addPdf(
        title: String,
        about: String?,
    ) {
        pdfAddResponse.load {
            validation(title, pdfFile?.absolutePath)
            pdfRepository.addNewPdf(
                pdfFile?.absolutePath ?: "",
                title,
                about,
                selectedTag?.id
            )
        }
    }

    fun downloadPdf(url: String, saveFolderPath: File, callBack : FileDownloadTool.DownloadCallback){
        viewModelScope.launch {
            FileDownloadTool.downloadFile(url,saveFolderPath,callBack)
        }
    }
}