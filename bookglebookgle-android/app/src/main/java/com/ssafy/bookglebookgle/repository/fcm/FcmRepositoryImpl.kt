package com.ssafy.bookglebookgle.repository.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.ssafy.bookglebookgle.network.api.FcmApi
import com.ssafy.bookglebookgle.network.api.FcmTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmRepositoryImpl @Inject constructor(
    private val api: FcmApi
) : FcmRepository {

    override fun registerTokenAsync(token: String?, uidFallback: Long?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t = token ?: FirebaseMessaging.getInstance().token.await()
                val res = api.registerToken(FcmTokenRequest(t), uidFallback)
                if (res.isSuccessful) {
                    Log.i("FCM", "토큰 등록 성공")
                } else {
                    Log.w("FCM", "토큰 등록 실패: ${res.code()} ${res.message()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "토큰 등록 중 예외", e)
            }
        }
    }
}
