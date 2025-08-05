package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import com.ssafy.bookglebookgle.repository.ChatRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatRoomUiState(
    val chatMessages: List<ChatMessage> = emptyList(),
    val currentChatRoom: ChatMessagesResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepositoryImpl: ChatRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    // 채팅방 입장 및 메시지 불러오기
    fun enterChatRoom(groupId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val chatMessagesResponse = chatRepositoryImpl.getChatMessages(groupId)
                _uiState.value = _uiState.value.copy(
                    currentChatRoom = chatMessagesResponse,
                    chatMessages = chatMessagesResponse.messages,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "채팅방 불러오기 실패: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    // 새 메시지 추가 (gRPC로 실시간 메시지 받았을 때)
    fun addNewMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.chatMessages.toMutableList()
        currentMessages.add(message)
        _uiState.value = _uiState.value.copy(chatMessages = currentMessages)
    }

    // 에러 초기화
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}