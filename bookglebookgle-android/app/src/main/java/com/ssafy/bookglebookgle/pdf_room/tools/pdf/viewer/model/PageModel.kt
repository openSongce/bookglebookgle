package com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model

data class PageModel(
    val width: Float, // 페이지의 가로 길이
    val height: Float, // 페이지의 세로 길이
    val coordinates: ArrayList<PdfLine>, // 페이지 안의 글자/객체들의 위치 정보 리스트
) {
    var relativeSizeCalculated = false // 	상대적인 크기 계산 여부 플래그 (기본값: false)
}
