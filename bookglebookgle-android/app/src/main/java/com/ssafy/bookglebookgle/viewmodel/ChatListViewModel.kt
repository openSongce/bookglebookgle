package com.ssafy.bookglebookgle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ChatListResponse
import com.ssafy.bookglebookgle.repository.ChatRepository
import com.ssafy.bookglebookgle.repository.ChatRepositoryImpl
import com.ssafy.bookglebookgle.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val isLoading: Boolean = false,
    val chatList: List<ChatListResponse> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
     private val chatRepositoryImpl: ChatRepositoryImpl
) : ViewModel() {

    companion object {
        private const val TAG = "ChatListViewModel"
    }

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        loadChatList()
    }

    private fun loadChatList() {
        viewModelScope.launch {
            Log.d(TAG, "채팅방 목록 로딩 시작")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // TODO: Repository에서 채팅 목록 가져오기
//                 val chatList = chatRepositoryImpl.getChatList()

//                Log.d(TAG, "채팅방 목록 로딩 성공 - ${chatList.size}개 채팅방")

//                _uiState.value = _uiState.value.copy(
//                    isLoading = false,
//                    chatList = chatList
//                )

                // Todo: 실제 연동 시 아래 코드 제거.
                val dummyChatList = listOf(
                    ChatListResponse(
                        chatRoomId = 101,
                        imageUrl = null,
                        category = "READING",
                        groupId = 1,
                        groupTitle = "책 읽기 모임",
                        lastMessage = "다음 주에 읽을 책이 뭐가 좋을까요?",
                        lastMessageTime = "2024-12-21 14:30",
                        memberCount = 5,
                        unreadCount = 2
                    ),
                    ChatListResponse(
                        chatRoomId = 102,
                        imageUrl = null,
                        category = "REVIEW",
                        groupId = 2,
                        groupTitle = "자소서 첨삭 모임",
                        lastMessage = "첨삭해주신 내용 확인했습니다!",
                        lastMessageTime = "2024-12-21 12:15",
                        memberCount = 3,
                        unreadCount = 0
                    ),
                    ChatListResponse(
                        chatRoomId = 103,
                        imageUrl = null,
                        category = "STUDY",
                        groupId = 3,
                        groupTitle = "정처기 모임",
                        lastMessage = "내일 시험 준비는 어떻게 되가고 있나요?",
                        lastMessageTime = "2024-12-20 18:45",
                        memberCount = 6,
                        unreadCount = 5
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    chatList = dummyChatList
                )

            }  catch (e: Exception) {
                Log.e(TAG, "채팅방 목록 로딩 실패: ${e.message}", e)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "채팅방 목록을 불러오는데 실패했습니다."
                )
            }
        }
    }

    fun refreshChatList() {
        loadChatList()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // 특정 채팅방의 읽지 않은 메시지 수 업데이트 (실시간 알림 등에서 사용)
    fun updateUnreadCount(chatRoomId: Long, newUnreadCount: Int) {
        val currentList = _uiState.value.chatList
        val updatedList = currentList.map { chat ->
            if (chat.chatRoomId == chatRoomId) {
                chat.copy(unreadCount = newUnreadCount)
            } else {
                chat
            }
        }

        _uiState.value = _uiState.value.copy(chatList = updatedList)
        Log.d(TAG, "채팅방 ${chatRoomId}의 읽지않은메시지 수 업데이트: $newUnreadCount")
    }

    // 새로운 메시지가 도착했을 때 목록 업데이트
    fun updateLastMessage(chatRoomId: Long, lastMessage: String, lastMessageTime: String) {
        val currentList = _uiState.value.chatList
        val updatedList = currentList.map { chat ->
            if (chat.chatRoomId == chatRoomId) {
                chat.copy(
                    lastMessage = lastMessage,
                    lastMessageTime = lastMessageTime,
                    unreadCount = chat.unreadCount + 1
                )
            } else {
                chat
            }
        }.sortedByDescending { it.lastMessageTime } // 최신 메시지 순으로 정렬

        _uiState.value = _uiState.value.copy(chatList = updatedList)
        Log.d(TAG, "채팅방 ${chatRoomId}의 마지막 메시지 업데이트")
    }
}