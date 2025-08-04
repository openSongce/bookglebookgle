package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.entity.GroupListResponse
import com.ssafy.bookglebookgle.entity.MyGroupResponse
import com.ssafy.bookglebookgle.network.api.GroupApi
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject

private const val TAG = "싸피_GroupRepository"
class GroupRepositoryImpl @Inject constructor(
    private val groupApi: GroupApi
) : GroupRepository{
    /**
    * 그룹 생성 (OCR 포함)
    * @param groupInfo 그룹 정보 JSON
    * @param file PDF 파일
    * @return API 응답
    */
    override suspend fun createGroup(
        groupInfo: RequestBody,
        file: MultipartBody.Part,
    ): Response<ResponseBody> {
        return try {
            Log.d(TAG, "그룹 생성(OCR 포함) 요청 시작")

            val response = groupApi.createGroup(
                groupInfo = groupInfo,
                file = file
            )

            if (response.isSuccessful) {
                Log.d(TAG, "그룹 생성(OCR 포함) 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "그룹 생성(OCR 포함) 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.d(TAG, "그룹 생성(OCR 포함) 네트워크 오류: ${e.message}")
            throw Exception("그룹 생성(OCR 포함) 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * 그룹 생성 (OCR 없음)
     * @param groupInfo 그룹 정보 JSON
     * @param file PDF 파일
     * @return API 응답
     */
    override suspend fun createGroupWithoutOcr(
        groupInfo: RequestBody,
        file: MultipartBody.Part,
    ): Response<ResponseBody> {
        return try {
            Log.d(TAG, "그룹 생성(OCR 없음) 요청 시작")

            val response = groupApi.createGroupWithoutOcr(
                groupInfo = groupInfo,
                file = file
            )

            if (response.isSuccessful) {
                Log.d(TAG, "그룹 생성(OCR 없음) 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "그룹 생성(OCR 없음) 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.d(TAG, "그룹 생성(OCR 없음) 네트워크 오류: ${e.message}")
            throw Exception("그룹 생성(OCR 없음) 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    override suspend fun getAllGroups(): Response<List<GroupListResponse>> {
        return try {
            Log.d(TAG, "모든 그룹 리스트 조회 요청 시작")
            val response = groupApi.getAllGroups()

            if (response.isSuccessful) {
                Log.d(TAG, "모든 그룹 리스트 조회 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "모든 그룹 리스트 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "모든 그룹 리스트 조회 실패 - 네트워크 오류: ${e.message}")
            throw Exception("그룹 리스트 조회 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * 모임 상세 조회
     * @param groupId 그룹 ID
     * @return 그룹 상세 정보
     */
    override suspend fun getGroupDetail(groupId: Long): Response<GroupDetailResponse> {
        return try {
            Log.d(TAG, "모임 상세 조회 요청 시작 - groupId: $groupId")

            val response = groupApi.getGroupDetail(groupId)

            if (response.isSuccessful) {
                Log.d(TAG, "모임 상세 조회 성공 - 응답코드: ${response.code()}")
                response.body()?.let { groupDetail ->
                    Log.d(TAG, "조회된 모임 정보 - 제목: ${groupDetail.roomTitle}, 카테고리: ${groupDetail.category}")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "모임 상세 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "모임 상세 조회 실패 - 네트워크 오류: ${e.message}")
            throw Exception("모임 상세 조회 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * 내 모임 조회
     * @return 내가 참여한 그룹 리스트
     */
    override suspend fun getMyGroups(): Response<List<MyGroupResponse>> {
        return try {
            Log.d(TAG, "내 모임 조회 요청 시작")

            val response = groupApi.getMyGroups()

            if (response.isSuccessful) {
                Log.d(TAG, "내 모임 조회 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "내 모임 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "내 모임 조회 실패 - 네트워크 오류: ${e.message}")
            throw Exception("내 모임 조회 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    override suspend fun joinGroup(groupId: Long): Response<ResponseBody> {
        return try {
            Log.d(TAG, "모임 가입 요청 시작 - groupId: $groupId")

            val response = groupApi.joinGroup(groupId)

            if (response.isSuccessful) {
                Log.d(TAG, "모임 가입 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "모임 가입 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "모임 가입 실패 - 네트워크 오류: ${e.message}")
            throw Exception("모임 가입 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    override suspend fun deleteGroup(groupId: Long): Response<ResponseBody> {
        return try {
            Log.d(TAG, "모임 삭제 요청 시작 - groupId: $groupId")

            val response = groupApi.deleteGroup(groupId)

            if (response.isSuccessful) {
                Log.d(TAG, "모임 삭제 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "모임 삭제 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "모임 삭제 실패 - 네트워크 오류: ${e.message}")
            throw Exception("모임 삭제 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

}