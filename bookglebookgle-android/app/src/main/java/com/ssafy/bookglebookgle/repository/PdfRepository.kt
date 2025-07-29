package com.ssafy.bookglebookgle.repository

import java.io.File

interface PdfRepository {
    suspend fun uploadPdf(file: File): Boolean
}
