package com.ssafy.bookglebookgle.network.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface PdfApi {
    /**
     * PDF 파일 업로드 API
     * */
    @Multipart
    @POST("pdf/upload")
    suspend fun uploadPdf(
        @Part file: MultipartBody.Part,
    ): Response<Unit>

    /**
     * 특정 그룹의 PDF 파일 목록 조회 API
     * @param groupId 그룹 ID
     * @return PDF 파일 목록
     */
    @GET("group/{groupId}/pdf")
    suspend fun getGroupPdf(
        @Path("groupId") groupId: Long
    ): Response<ResponseBody>
}