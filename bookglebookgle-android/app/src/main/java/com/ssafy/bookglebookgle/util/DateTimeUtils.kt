package com.ssafy.bookglebookgle.util

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
object DateTimeUtils {

    private const val TAG = "DateTimeUtils"

    // 다양한 ISO 8601 형식을 처리할 수 있는 포맷터들
    private val formatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.S"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    )

    // 한국어 시간 형식으로 변환하는 포맷터 (시간 앞의 0 제거)
    private val koreanTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    /**
     * ISO 8601 형식의 날짜 문자열을 한국어 시간 형식으로 변환
     * @param isoDateString "2025-08-06T16:33:10.311" 형식의 문자열
     * @return "오후 4:33" 형식의 문자열
     */
    fun formatChatTime(isoDateString: String): String {
        if (isoDateString.isBlank()) {
            Log.w(TAG, "빈 문자열이 전달됨")
            return ""
        }

        // 여러 포맷터를 순서대로 시도
        for (formatter in formatters) {
            try {
                val dateTime = LocalDateTime.parse(isoDateString, formatter)
                val formattedTime = dateTime.format(koreanTimeFormatter)
                Log.d(TAG, "시간 포맷팅 성공: $isoDateString -> $formattedTime")
                return formattedTime
            } catch (e: DateTimeParseException) {
                // 다음 포맷터로 시도
                continue
            }
        }

        // 모든 포맷터 실패 시 수동으로 시간 부분 추출 및 변환
        Log.w(TAG, "모든 포맷터 실패, 수동 처리 시도: $isoDateString")
        return try {
            // "2025-08-08T02:31:58.89" 에서 "02:31:58" 부분 추출
            val timePart = when {
                isoDateString.contains('T') -> {
                    val timeSection = isoDateString.split('T')[1]
                    timeSection.split('.')[0] // 밀리초 제거
                }
                else -> isoDateString
            }

            // "HH:mm:ss" 또는 "HH:mm" 형식에서 시간과 분 추출
            val timeParts = timePart.split(':')
            if (timeParts.size >= 2) {
                val hour = timeParts[0].toIntOrNull() ?: 0
                val minute = timeParts[1].toIntOrNull() ?: 0

                // 24시간 형식을 12시간 형식으로 변환
                val (period, displayHour) = when {
                    hour == 0 -> "오전" to 12
                    hour < 12 -> "오전" to hour
                    hour == 12 -> "오후" to 12
                    else -> "오후" to (hour - 12)
                }

                val formattedTime = "$period ${displayHour}:${String.format("%02d", minute)}"
                Log.d(TAG, "수동 포맷팅 성공: $isoDateString -> $formattedTime")
                return formattedTime
            } else {
                Log.e(TAG, "시간 파싱 실패: $isoDateString")
                return isoDateString
            }
        } catch (e: Exception) {
            Log.e(TAG, "수동 처리도 실패: $isoDateString", e)
            isoDateString // 모든 처리 실패 시 원본 반환
        }
    }

    /**
     * 채팅 메시지의 날짜를 더 자세한 형식으로 변환 (필요시 사용)
     * @param isoDateString "2025-08-06T16:33:10.311" 형식의 문자열
     * @return "8월 6일 오후 4:33" 형식의 문자열
     */
    fun formatChatDateTime(isoDateString: String): String {
        if (isoDateString.isBlank()) {
            return ""
        }

        for (formatter in formatters) {
            try {
                val dateTime = LocalDateTime.parse(isoDateString, formatter)
                val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
                return dateTime.format(dateFormatter)
            } catch (e: DateTimeParseException) {
                continue
            }
        }

        return isoDateString
    }

    /**
     * 디버깅용 - 원본 시간 문자열 로깅
     */
    fun logTimeString(isoDateString: String, context: String = "") {
        Log.d(TAG, "[$context] 시간 문자열: '$isoDateString'")
    }
}