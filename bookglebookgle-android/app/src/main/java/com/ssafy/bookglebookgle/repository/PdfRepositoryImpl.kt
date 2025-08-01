package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.network.api.PdfApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import javax.inject.Inject

private const val TAG = "싸피_PdfRepositoryImpl"
class PdfRepositoryImpl @Inject constructor(
    private val pdfApi: PdfApi
) : PdfRepository {

    override suspend fun uploadPdf(file: File): Boolean {
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
                Log.d("PdfUpload", "업로드 실패 ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e("PdfUpload", "예외 발생", e)
            false
        }
    }

    override suspend fun getGroupPdf(groupId: Long): InputStream? {
        return try {
            val response = pdfApi.getGroupPdf(groupId)
            if (response.isSuccessful) {
                response.body()?.byteStream()
            } else {
                Log.d(TAG, "PDF 다운로드 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "PDF 다운로드 중 예외 발생: ${e.message}", e)
            null
        }
    }

}
