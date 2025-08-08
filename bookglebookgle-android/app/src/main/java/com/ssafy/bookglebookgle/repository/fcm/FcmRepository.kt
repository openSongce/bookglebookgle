package com.ssafy.bookglebookgle.repository.fcm

interface FcmRepository {
    fun registerTokenAsync(token: String? = null, uidFallback: Long? = null)
}