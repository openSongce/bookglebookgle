package com.ssafy.bookglebookgle.pdf.response

import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.BookmarkModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.CommentModel
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.HighlightModel

// 모든 주석을 담는 데이터 클래스
data class AnnotationListResponse(
    val comments: ArrayList<CommentModel> = arrayListOf(),
    val highlights: ArrayList<HighlightModel> = arrayListOf(),
    val bookmarks: ArrayList<BookmarkModel> = arrayListOf()
)