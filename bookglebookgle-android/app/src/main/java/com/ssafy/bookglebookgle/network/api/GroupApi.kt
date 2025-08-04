package com.ssafy.bookglebookgle.network.api

import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.entity.GroupListResponse
import com.ssafy.bookglebookgle.entity.MyGroupResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

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

    /**
     * 모임 상세 조회
     * */
    @GET("group/{groupId}")
    suspend fun getGroupDetail(
        @Path("groupId") groupId: Long
    ): Response<GroupDetailResponse>

    /**
     * 내 모임 조회
     * */
    @GET("group/my")
    suspend fun getMyGroups(): Response<List<MyGroupResponse>>

    /**
     * 모임 가입
     * */
    @POST("group/{groupId}/join")
    suspend fun joinGroup(
        @Path("groupId") groupId: Long
    ): Response<ResponseBody>

    /**
     * 모임 삭제
     * */
    @DELETE("group/{groupId}")
    suspend fun deleteGroup(
        @Path("groupId") groupId: Long
    ): Response<ResponseBody>

    /**
     * 모임 수정
     * */
    @PUT("group/{groupId}/edit")
    suspend fun editGroup(
        @Path("groupId") groupId: Long,
        @Part("groupInfo") groupInfo: RequestBody,
    ): Response<ResponseBody>

}