package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.UploadPdfResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PdfApi {
    @Multipart
    @POST("pdf/upload")
    suspend fun uploadPdf(
        @Part file: MultipartBody.Part,
    ): Response<UploadPdfResponse>
}