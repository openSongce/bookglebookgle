package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.GroupListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface GroupApi {
    /**
     * 그룹 생성 (OCR 포함)
     */
    @Multipart
    @POST("group/create")
    suspend fun createGroup(
        @Part("groupInfo") groupInfo: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<ResponseBody>

    /**
     * 그룹 생성 (OCR 없음)
     */
    @Multipart
    @POST("group/create/no-ocr")
    suspend fun createGroupWithoutOcr(
        @Part("groupInfo") groupInfo: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<ResponseBody>

    /**
     * 모임 전체 조회
     * */
    @GET("group/list")
    suspend fun getAllGroups(): Response<List<GroupListResponse>>
}