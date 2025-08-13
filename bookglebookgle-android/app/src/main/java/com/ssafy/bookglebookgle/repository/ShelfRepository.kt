package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.ReadBook

interface ShelfRepository {
    suspend fun fetchReadBooks(userId: Long): List<ReadBook>
}