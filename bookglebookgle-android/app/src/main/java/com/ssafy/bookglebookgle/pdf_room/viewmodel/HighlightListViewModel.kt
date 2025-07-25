package com.ssafy.bookglebookgle.pdf_room.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf_room.repository.PDFRepository
import com.ssafy.bookglebookgle.pdf_room.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HighlightListViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {
    val deleteHighlightResponse = OperationsStateHandler(viewModelScope)

    fun deleteHighlights(ids: List<Long>) {
        deleteHighlightResponse.load {
            pdfRepository.deleteHighlight(ids)
        }
    }
}