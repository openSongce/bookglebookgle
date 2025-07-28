package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.network.api.PdfApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class PdfRepository @Inject constructor(
    private val pdfApi: PdfApi
) {
    suspend fun uploadPdf(file: File): Boolean {
        return try {
            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = pdfApi.uploadPdf(filePart)
            Log.d("PdfUpload", "서버 응답 코드: ${response.code()}")
            Log.d("PdfUpload", "서버 응답 메시지: ${response.message()}")

            if (response.isSuccessful) {
                Log.d("PdfUpload", "업로드 성공")
                true
            } else {
                Log.d("PdfUpload","업로드 실패 ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e("PdfUpload", "예외 발생", e)
            false
        }
    }

}
