package com.ssafy.bookglebookgle.repository

import java.io.File
import java.io.InputStream

interface PdfRepository {
    suspend fun uploadPdf(file: File): Boolean
    suspend fun getGroupPdf(groupId: Long): InputStream?
}
