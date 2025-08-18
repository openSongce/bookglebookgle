package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.ReadBook
import javax.inject.Inject

class ShelfRepositoryImpl @Inject constructor(): ShelfRepository{
    override suspend fun fetchReadBooks(userId: Long): List<ReadBook> {
        // 오래된 → 최신 순(아래에서 위로 쌓일 때 최신이 제일 위로 오게 하려고)
        val now = System.currentTimeMillis()
        return listOf(
            ReadBook(1, "객체지향의 사실과 오해"),
            ReadBook(2, "클린 아키텍처"),
            ReadBook(3, "도메인 주도 설계"),
            ReadBook(4, "Real World HTTP"),
        )
    }
}