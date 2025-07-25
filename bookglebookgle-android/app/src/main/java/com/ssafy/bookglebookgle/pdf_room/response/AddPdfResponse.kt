package com.ssafy.bookglebookgle.pdf_room.response

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class PdfNotesResponse(
    val notes : List<PdfNoteListModel>
)

// PDF 한 개의 노트 정보를 담는 데이터 클래스
@Parcelize
data class PdfNoteListModel(
    val id: Long,
    val title: String,
    val tag: TagModel?,
    val about: String?,
    val filePath: String,
    val updatedTime: Long
): Parcelable

// PDF에 포함되어 있는 태그 정보를 담는 데이터 클래스
@Parcelize
data class TagModel(
    val id: Long,
    val title: String,
    val colorCode: String?
): Parcelable