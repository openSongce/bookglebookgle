package com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * PDF에 주석(댓글, 하이라이트), 북마크 정보를 저장하는 데이터 클래스들
 *
 * Parcelable 을 사용해 화면 간 전달 및 저장 가능
 *
 * 상속을 통해 공통 속성(PdfAnnotationModel) 관리
 *
 * 선택 여부는 @IgnoredOnParcel로 직렬화 대상에서 제외되어 UI 용도에만 사용
 * */
@Parcelize
data class CommentModel(
    val id: Long, // 주석의 고유 ID
    val snippet: String, // 주석이 달린 문서의 일부 텍스트
    var text: String, // 사용자가 입력한 주석 내용
    val page: Int, // 주석이 달린 실제 페이지 번호 (1부터 시작)
//    var updatedAt: Long, //	마지막 수정 시각 (timestamp)
    val coordinates: Coordinates?, // 주석이 위치한 좌표 정보
) : PdfAnnotationModel(Type.Note, page - 1), Parcelable {
    @IgnoredOnParcel
    var isSelected = false

    /**이 기능은 Pagination Page Index 및 Annotation 유형과 같은 상위 주석 클래스에서 THA 값을 업데이트합니다.*/
    fun updateAnnotationData(): CommentModel {
        super.paginationPageIndex = page - 1
        super.type = Type.Note
        return this
    }
}

@Parcelize
data class HighlightModel(
    val id: Long, // 고유 ID
    val snippet: String, // 하이라이트된 텍스트 일부
    val color: String, // 하이라이트 색상 (hex string 등)
    val page: Int, // 실제 페이지 번호
//    val updatedAt: Long, // 마지막 수정 시각
    val coordinates: Coordinates?, // 하이라이트 위치
) : PdfAnnotationModel(Type.Highlight, page - 1), Parcelable {
    @IgnoredOnParcel
    var isSelected = false


    /**이 기능은 Pagination Page Index 및 Annotation 유형과 같은 상위 주석 클래스에서 THA 값을 업데이트합니다.*/
    fun updateAnnotationData(): HighlightModel {
        super.paginationPageIndex = page - 1
        super.type = Type.Highlight
        return this
    }
}

@Parcelize
data class BookmarkModel(
    val id: Long, // 고유 ID
    val page: Int, // 북마크가 지정된 페이지 번호
//    val updatedAt: Long, // 마지막 북마크 시간
) : Parcelable {
    @IgnoredOnParcel
    var isSelected = false
}

@Parcelize
data class Coordinates(
    var startX: Double, // 	좌측 상단 좌표
    var startY: Double,
    var endX: Double, // 우측 하단 좌표
    var endY: Double,
) : Parcelable
