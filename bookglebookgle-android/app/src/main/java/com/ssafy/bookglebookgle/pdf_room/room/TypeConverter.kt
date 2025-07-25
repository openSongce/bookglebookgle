package com.ssafy.bookglebookgle.pdf_room.room

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.Coordinates

/**
 * 좌표를 DB에 저장할 수 있는 자료형으로 변환
 * 실제 서버 API 사용 시엔 JSON 변환은 Retrofit + Gson이 자동 처리하므로 필요 없음.
 * */
object TypeConverter {
    @TypeConverter
    fun toPdfCoordinates(json: String?): Coordinates? {
        if (json.isNullOrEmpty()) return null
        return Gson().fromJson(json, Coordinates::class.java)
    }

    // Coordinates(10.5, 22.7) → "{"x":10.5,"y":22.7}"
    @TypeConverter
    fun fromPdfCoordinates(topic: Coordinates?): String? {
        if (topic == null) return null
        return Gson().toJson(topic)
    }
}
