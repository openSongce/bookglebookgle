package com.ssafy.bookglebookgle.repository

import com.ssafy.bookglebookgle.entity.GroupDetail
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.entity.GroupListResponse
import com.ssafy.bookglebookgle.entity.MyGroupResponse
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

    suspend fun getGroupDetail(groupId: Long): GroupDetail

    suspend fun getMyGroups(): Response<List<MyGroupResponse>>

    suspend fun joinGroup(groupId: Long): Response<ResponseBody>

    suspend fun deleteGroup(groupId: Long): Response<ResponseBody>

    suspend fun editGroup(
        groupId: Long,
        groupInfo: RequestBody,
    ): Response<ResponseBody>

    suspend fun searchGroups(roomTitle: String): Response<List<GroupListResponse>>

    suspend fun leaveGroup(groupId: Long): Response<ResponseBody>

    suspend fun rateMember(groupId: Long, toId: Long, score: Float): Response<Unit>

}
