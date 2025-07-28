package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.network.api.GroupApi
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject

private const val TAG = "싸피_GroupRepository"
class GroupRepository @Inject constructor(
    private val groupApi: GroupApi
){
    /**
    * 그룹 생성 (OCR 포함)
    * @param groupInfo 그룹 정보 JSON
    * @param file PDF 파일
    * @return API 응답
    */
    suspend fun createGroup(
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
                Log.d(TAG, "그룹 생성(OCR 포함) 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
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
    suspend fun createGroupWithoutOcr(
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
                Log.d(TAG, "그룹 생성(OCR 없음) 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
            }

            response
        } catch (e: Exception) {
            Log.d(TAG, "그룹 생성(OCR 없음) 네트워크 오류: ${e.message}")
            throw Exception("그룹 생성(OCR 없음) 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
}