package com.ssafy.bookglebookgle.pdf_room.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.pdf_room.repository.PDFRepository
import com.ssafy.bookglebookgle.pdf_room.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BookmarkListViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {
    val deleteBookmarksResponse = OperationsStateHandler(viewModelScope)

    fun deleteBookmarks(ids: List<Long>) {
        deleteBookmarksResponse.load {
            pdfRepository.deleteBookmarks(ids)
        }
    }
}