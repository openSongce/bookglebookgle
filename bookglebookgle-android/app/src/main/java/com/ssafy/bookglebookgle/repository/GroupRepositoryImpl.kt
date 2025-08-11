package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.entity.GroupDetail
import com.ssafy.bookglebookgle.entity.GroupDetailResponse
import com.ssafy.bookglebookgle.entity.GroupListResponse
import com.ssafy.bookglebookgle.entity.MyGroupResponse
import com.ssafy.bookglebookgle.entity.toDomain
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

            val response = groupApi.createGroup(
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
    override suspend fun getGroupDetail(groupId: Long): GroupDetail {
        return try {
            Log.d(TAG, "모임 상세 조회 요청 시작 - groupId: $groupId")

            val resp = groupApi.getGroupDetail(groupId)

            if (!resp.isSuccessful) {
                val errorBody = resp.errorBody()?.string()
                Log.d(TAG, "모임 상세 조회 실패 - code=${resp.code()}, msg=${resp.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
                throw Exception("모임 상세 조회 실패(code=${resp.code()})")
            }

            val body = resp.body()
                ?: throw Exception("모임 상세 조회 실패: 응답 바디가 비어있음")

            // 로깅은 너무 길지 않게 핵심만
            Log.d(
                TAG,
                "모임 상세 조회 성공 - 제목=${body.roomTitle}, 카테고리=${body.category}, " +
                        "isCompleted=${body.isCompleted}, 멤버수=${body.members.size}, " +
                        "평점제출완료멤버=${body.members.count { it.isCompleted }}"
            )

            // ★ 도메인으로 매핑해서 반환
            body.toDomain()

        } catch (e: Exception) {
            Log.e(TAG, "모임 상세 조회 실패 - ${e.message}", e)
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

    /**
     * 모임 가입
     * @param groupId 그룹 ID
     * @return API 응답
     */
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

    /**
     * 모임 삭제
     * @param groupId 그룹 ID
     * @return API 응답
     */
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

    /**
     * 모임 수정
     * @param groupId 그룹 ID
     * @param groupInfo 수정할 그룹 정보 JSON
     * @return API 응답
     */
    override suspend fun editGroup(
        groupId: Long,
        groupInfo: RequestBody,
    ): Response<ResponseBody> {
        return try {
            Log.d(TAG, "모임 수정 요청 시작 - groupId: $groupId")

            val response = groupApi.editGroup(groupId, groupInfo)

            if (response.isSuccessful) {
                Log.d(TAG, "모임 수정 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "모임 수정 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "모임 수정 실패 - 네트워크 오류: ${e.message}")
            throw Exception("모임 수정 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * 모임 검색
     * @param roomTitle 검색할 방 제목
     * @return 검색된 그룹 리스트
     */
    override suspend fun searchGroups(roomTitle: String): Response<List<GroupListResponse>> {
        return try {
            Log.d(TAG, "모임 검색 요청 시작 - roomTitle: $roomTitle")

            val response = groupApi.searchGroups(roomTitle)

            if (response.isSuccessful) {
                Log.d(TAG, "모임 검색 성공 - 응답코드: ${response.code()}")
                response.body()?.let { groups ->
                    Log.d(TAG, "검색 결과 - 총 ${groups.size}개 모임 발견")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "모임 검색 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "모임 검색 실패 - 네트워크 오류: ${e.message}")
            throw Exception("모임 검색 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * 모임 탈퇴
     * @param groupId 그룹 ID
     * @return API 응답
     */
    override suspend fun leaveGroup(groupId: Long): Response<ResponseBody> {
        return try {
            Log.d(TAG, "모임 탈퇴 요청 시작 - groupId: $groupId")

            val response = groupApi.leaveGroup(groupId)

            if (response.isSuccessful) {
                Log.d(TAG, "모임 탈퇴 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "모임 탈퇴 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "모임 탈퇴 실패 - 네트워크 오류: ${e.message}")
            throw Exception("모임 탈퇴 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    override suspend fun rateMember(groupId: Long, toId: Long, score: Float): Response<Unit> {
        return try {
            Log.d(TAG, "멤버 평점 등록 요청 - groupId=$groupId, toId=$toId, score=$score")
            val resp = groupApi.rateMember(groupId, toId, score)
            if (resp.isSuccessful) {
                Log.d(TAG, "멤버 평점 등록 성공 - code=${resp.code()}")
            } else {
                val body = resp.errorBody()?.string()
                Log.d(TAG, "멤버 평점 등록 실패 - code=${resp.code()}, msg=${resp.message()}, body=$body")
            }
            resp
        } catch (e: Exception) {
            Log.e(TAG, "멤버 평점 등록 예외 - ${e.message}", e)
            throw Exception("멤버 평점 등록 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

}