package com.ssafy.bookglebookgle.util

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
object DateTimeUtils {

    // ISO 8601 형식의 날짜 문자열을 파싱하는 포맷터
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    // 한국어 시간 형식으로 변환하는 포맷터 (시간 앞의 0 제거)
    private val koreanTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    /**
     * ISO 8601 형식의 날짜 문자열을 한국어 시간 형식으로 변환
     * @param isoDateString "2025-08-06T16:33:10.311" 형식의 문자열
     * @return "오후 4:33" 형식의 문자열
     */
    fun formatChatTime(isoDateString: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDateString, isoFormatter)
            dateTime.format(koreanTimeFormatter)
        } catch (e: Exception) {
            // 파싱 실패 시 원본 문자열의 시간 부분만 반환
            try {
                val timePart = isoDateString.substring(11, 16) // HH:mm 부분 추출
                timePart
            } catch (ex: Exception) {
                isoDateString // 모든 처리 실패 시 원본 반환
            }
        }
    }

    /**
     * 채팅 메시지의 날짜를 더 자세한 형식으로 변환 (필요시 사용)
     * @param isoDateString "2025-08-06T16:33:10.311" 형식의 문자열
     * @return "8월 6일 오후 4:33" 형식의 문자열
     */
    fun formatChatDateTime(isoDateString: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDateString, isoFormatter)
            val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
            dateTime.format(dateFormatter)
        } catch (e: Exception) {
            isoDateString
        }
    }
}