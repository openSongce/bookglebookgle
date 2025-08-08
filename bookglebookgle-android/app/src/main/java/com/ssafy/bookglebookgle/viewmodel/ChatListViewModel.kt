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

    fun loadChatList() {
        viewModelScope.launch {
            Log.d(TAG, "채팅방 목록 로딩 시작")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                 val chatList = chatRepositoryImpl.getChatList()

                Log.d(TAG, "채팅방 목록 로딩 성공 - ${chatList.size}개 채팅방")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    chatList = chatList
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

    // 특정 그룹의 읽지 않은 메시지 수 업데이트 (groupId 기준)
    fun updateUnreadCount(groupId: Long, newUnreadCount: Int) {
        val currentList = _uiState.value.chatList
        val updatedList = currentList.map { chat ->
            if (chat.groupId == groupId) {
                chat.copy(unreadCount = newUnreadCount)
            } else {
                chat
            }
        }

        _uiState.value = _uiState.value.copy(chatList = updatedList)
        Log.d(TAG, "그룹 ${groupId}의 읽지않은메시지 수 업데이트: $newUnreadCount")
    }

    // 새로운 메시지가 도착했을 때 목록 업데이트 (groupId 기준)
    fun updateLastMessage(groupId: Long, lastMessage: String, lastMessageTime: String) {
        val currentList = _uiState.value.chatList
        val updatedList = currentList.map { chat ->
            if (chat.groupId == groupId) {
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
        Log.d(TAG, "그룹 ${groupId}의 마지막 메시지 업데이트")
    }

    // 채팅방 입장 시 읽지 않은 메시지 수 초기화 (groupId 기준)
    fun markAsRead(groupId: Long) {
        updateUnreadCount(groupId, 0)
        Log.d(TAG, "그룹 ${groupId} 메시지 읽음 처리")
    }
}