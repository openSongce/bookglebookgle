package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ReadBook
import com.ssafy.bookglebookgle.repository.ShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val repo: ShelfRepository
) : ViewModel() {

    private val _books = MutableStateFlow<List<ReadBook>>(emptyList())
    val books: StateFlow<List<ReadBook>> = _books

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(userId: Long) = viewModelScope.launch {
        _isLoading.value = true
        runCatching { repo.fetchReadBooks(userId) }
            .onSuccess { list ->
                // 오래된 → 최신 순(최신이 가장 위에 쌓이도록)
                _books.value = list.sortedBy { it.id }
            }
            .also { _isLoading.value = false }
    }

    fun addDummyOne(title: String) {
        val now = System.currentTimeMillis()
        val newId = (_books.value.maxOfOrNull { it.id } ?: 0L) + 1
        val newBook = ReadBook(newId, title)
        _books.value = (_books.value + newBook).sortedBy { it.id }
    }
}
