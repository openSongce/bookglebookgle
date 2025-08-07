package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.example.bookglebookgleserver.chat.ChatMessage
import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import com.ssafy.bookglebookgle.network.api.ChatApi
import retrofit2.Response
import javax.inject.Inject

private const val TAG = "싸피_ChatRepositoryImpl"
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi
) : ChatRepository {

    /**
     * 채팅 목록 조회
     * */
    override suspend fun getChatList(): List<ChatListResponse> {
        return try {
            Log.d(TAG, "채팅방 목록 요청 시작")

            val response = chatApi.getChatList()

            if (response.isSuccessful) {
                Log.d(TAG, "채팅방 목록 조회 성공 - 응답코드: ${response.code()}")
                response.body()?.let { chatList ->
                    Log.d(TAG, "채팅방 목록 - 총 ${chatList.size}개 채팅방 발견")
                    chatList.forEach { chat ->
                        Log.d(TAG, "제목: ${chat.groupTitle}, 읽지않은메시지: ${chat.unreadCount}")
                    }
                    chatList
                } ?: run {
                    Log.w(TAG, "채팅방 목록 조회 성공했지만 응답 body가 null")
                    emptyList()
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "채팅방 목록 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "채팅방 목록 조회 실패 - 네트워크 오류: ${e.message}")
            emptyList()
        }
    }

    /**
     * 채팅 메시지 조회 (페이징)
     * */
    override suspend fun getChatMessages(
        roomId: Long,
        beforeId: Long,
        size: Int
    ): List<ChatMessagesResponse> {
        return try {
            Log.d(TAG, "채팅 메시지 요청 시작 - roomId: $roomId, beforeId: $beforeId, size: $size")

            val response = chatApi.getChatMessages(roomId, beforeId, size)

            if (response.isSuccessful) {
                Log.d(TAG, "채팅 메시지 조회 성공 - 응답코드: ${response.code()}")
                response.body()?.let { messages ->
                    Log.d(TAG, "채팅 메시지 - 총 ${messages.size}개 메시지 발견")
                    messages.forEach { message ->
                        Log.d(TAG, "메시지 ID: ${message.id}, 보낸이: ${message.userNickname}, 내용: ${message.message}")
                    }
                    messages
                } ?: run {
                    Log.w(TAG, "채팅 메시지 조회 성공했지만 응답 body가 null")
                    emptyList()
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "채팅 메시지 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.e(TAG, "서버 에러 메시지: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "채팅 메시지 조회 실패 - 네트워크 오류: ${e.message}")
            emptyList()
        }
    }

    /**
     * 최신 채팅 메시지 조회 (초기 로드용)
     * beforeId 없이 최신 메시지부터 조회
     * */
    override suspend fun getLatestChatMessages(
        roomId: Long,
        size: Int
    ): List<ChatMessagesResponse> {
        return try {
            Log.d(TAG, "최신 채팅 메시지 요청 시작 - roomId: $roomId, size: $size")

            // beforeId를 0으로 설정하여 최신 메시지부터 조회
            val response = chatApi.getChatMessages(roomId, null, size)

            if (response.isSuccessful) {
                Log.d(TAG, "최신 채팅 메시지 조회 성공 - 응답코드: ${response.code()}")
                response.body()?.let { messages ->
                    Log.d(TAG, "최신 채팅 메시지 - 총 ${messages.size}개 메시지 조회")
                    messages.forEach { message ->
                        Log.d(TAG, "메시지 ID: ${message.id}, 유저ID: ${message.userId}, 닉네임: ${message.userNickname}, 내용: ${message.message} 시간: ${message.createdAt}")
                    }
                    messages
                } ?: run {
                    Log.w(TAG, "최신 채팅 메시지 조회 성공했지만 응답 body가 null")
                    emptyList()
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "최신 채팅 메시지 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.e(TAG, "서버 에러 메시지: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "최신 채팅 메시지 조회 실패 - 네트워크 오류: ${e.message}")
            emptyList()
        }
    }

    /**
     * 이전 채팅 메시지 조회 (스크롤 페이징용)
     * */
    override suspend fun getOlderChatMessages(
        roomId: Long,
        beforeMessageId: Long,
        size: Int
    ): List<ChatMessagesResponse> {
        return try {
            Log.d(TAG, "이전 채팅 메시지 요청 시작 - roomId: $roomId, beforeId: $beforeMessageId, size: $size")

            val response = chatApi.getChatMessages(roomId, beforeMessageId, size)

            if (response.isSuccessful) {
                Log.d(TAG, "이전 채팅 메시지 조회 성공 - 응답코드: ${response.code()}")
                response.body()?.let { messages ->
                    Log.d(TAG, "이전 채팅 메시지 - 총 ${messages.size}개 메시지 조회")
                    messages.forEach { message ->
                        Log.d(TAG, "메시지 ID: ${message.id}, 유저ID: ${message.userId}, 닉네임: ${message.userNickname}, 내용: ${message.message} 시간: ${message.createdAt}")
                    }
                    messages
                } ?: run {
                    Log.w(TAG, "이전 채팅 메시지 조회 성공했지만 응답 body가 null")
                    emptyList()
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "이전 채팅 메시지 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.e(TAG, "서버 에러 메시지: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "이전 채팅 메시지 조회 실패 - 네트워크 오류: ${e.message}")
            emptyList()
        }
    }

    /**
     * 채팅방 읽음 처리
     * */
    override suspend fun markChatAsRead(roomId: Long): Response<Unit> {
        return try {
            Log.d(TAG, "채팅방 읽음 처리 요청 시작 - roomId: $roomId")

            val response = chatApi.markChatAsRead(roomId)

            if (response.isSuccessful) {
                Log.d(TAG, "채팅방 읽음 처리 성공 - 응답코드: ${response.code()}")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "채팅방 읽음 처리 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.e(TAG, "서버 에러 메시지: $errorBody")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "채팅방 읽음 처리 실패 - 네트워크 오류: ${e.message}")
            throw e
        }
    }
}