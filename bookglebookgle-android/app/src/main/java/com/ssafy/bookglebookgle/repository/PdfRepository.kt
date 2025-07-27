package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.entity.UploadPdfResponse
import com.ssafy.bookglebookgle.network.api.PdfApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class PdfRepository @Inject constructor(
    private val pdfApi: PdfApi
) {
    suspend fun uploadPdf(file: File): UploadPdfResponse? {
        return try {
            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = pdfApi.uploadPdf(filePart)
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.d("PdfUpload","서버 응답 실패: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}
