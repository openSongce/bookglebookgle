package com.ssafy.bookglebookgle.repository

import android.util.Log
import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import com.ssafy.bookglebookgle.network.api.ChatApi
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
                        Log.d(TAG, "채팅방 ID: ${chat.chatRoomId}, 제목: ${chat.groupTitle}, 읽지않은메시지: ${chat.unreadCount}")
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

    override suspend fun getChatMessages(groupId: Long): ChatMessagesResponse {
        return try {
            Log.d(TAG, "채팅 메시지 요청 시작 - groupId: $groupId")

            val response = chatApi.getChatMessages(groupId)

            if (response.isSuccessful) {
                Log.d(TAG, "채팅 메시지 조회 성공 - 응답코드: ${response.code()}")
                response.body()?.let { chatMessages ->
                    Log.d(TAG, "채팅 메시지 - 총 ${chatMessages.messages.size}개 메시지 발견")
                    chatMessages.messages.forEach { message ->
                        Log.d(TAG, "메시지 ID: ${message.messageId}, 보낸이: ${message.nickname}, 내용: ${message.message}")
                    }
                    chatMessages
                } ?: run {
                    Log.w(TAG, "채팅 메시지 조회 성공했지만 응답 body가 null")
                    ChatMessagesResponse(
                        groupId = groupId,
                        messages = emptyList()
                    )
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "채팅 메시지 조회 실패 - 응답코드: ${response.code()}, 메시지: ${response.message()}")
                Log.d(TAG, "서버 에러 메시지: $errorBody")
                ChatMessagesResponse(
                    groupId = groupId,
                    messages = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "채팅 메시지 조회 실패 - 네트워크 오류: ${e.message}")
            ChatMessagesResponse(
                groupId = groupId,
                messages = emptyList()
            )
        }
    }
}