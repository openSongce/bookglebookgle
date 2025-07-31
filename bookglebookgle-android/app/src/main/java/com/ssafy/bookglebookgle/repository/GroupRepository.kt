package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.entity.GroupListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response

interface GroupRepository {
    suspend fun createGroup(
        groupInfo: RequestBody,
        file: MultipartBody.Part,
    ): Response<ResponseBody>

    suspend fun createGroupWithoutOcr(
        groupInfo: RequestBody,
        file: MultipartBody.Part,
    ): Response<ResponseBody>

    suspend fun getAllGroups(): Response<List<GroupListResponse>>

    suspend fun getGroupDetail(groupId: Long): Response<GroupDetailResponse>
}
