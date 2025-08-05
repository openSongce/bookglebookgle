package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import com.ssafy.bookglebookgle.repository.ChatRepositoryImpl
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import com.ssafy.bookglebookgle.repository.ChatGrpcRepository
import com.ssafy.bookglebookgle.repository.GrpcConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatRoomUiState(
    val chatMessages: List<ChatMessage> = emptyList(),
    val currentChatRoom: ChatMessagesResponse? = null,
    val groupTitle: String = "채팅방",
    val isLoading: Boolean = false,
    val error: String? = null,
    val grpcConnected: Boolean = false  // gRPC 연결 상태
)

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepositoryImpl: ChatRepositoryImpl,          // REST API
    private val groupRepositoryImpl: GroupRepositoryImpl,
    private val chatGrpcRepository: ChatGrpcRepository           // gRPC
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private val grpcMessageObserver = Observer<ChatMessage> { newMessage -> addNewMessage(newMessage) }

    init {
        chatGrpcRepository.newMessages.observeForever(grpcMessageObserver)
        observeGrpcConnection()
    }

    // gRPC 연결 상태 관찰
    private fun observeGrpcConnection() {
        chatGrpcRepository.connectionStatus.observeForever { status ->
            val isConnected = status == GrpcConnectionStatus.CONNECTED
            _uiState.value = _uiState.value.copy(grpcConnected = isConnected)

            when (status) {
                is GrpcConnectionStatus.ERROR -> {
                    _uiState.value = _uiState.value.copy(
                        error = "실시간 채팅 연결 오류: ${status.message}"
                    )
                }
                else -> {}
            }
        }
    }

    fun isMyMessage(message: ChatMessage, userId: Int): Boolean {
        return message.userId == userId
    }

    // 채팅방 입장 - REST API + gRPC 연결
    fun enterChatRoom(groupId: Long, userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // 그룹 정보 불러오기 (제목)
                val groupDetail = groupRepositoryImpl.getGroupDetail(groupId)
                val groupTitle = groupDetail.body()?.roomTitle

                // 기존 메시지 불러오기
                val chatMessagesResponse = chatRepositoryImpl.getChatMessages(groupId)

                _uiState.value = _uiState.value.copy(
                    currentChatRoom = chatMessagesResponse,
                    chatMessages = chatMessagesResponse.messages,  // 기존 메시지 (gRPC 이전 내용)
                    groupTitle = groupTitle ?: "채팅방",
                    isLoading = false
                )

                // gRPC 연결
                connectToGrpcChat(groupId, userId)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "채팅방 불러오기 실패: ${e.message}",
                    isLoading = false
                )
            }
        }
    }


    // gRPC 실시간 채팅 연결
    private fun connectToGrpcChat(groupId: Long, userId: Int) {
        viewModelScope.launch {
            try {
                // 사용자 정보 가져오기
                val userName = "사용자 $userId" // 임시 이름

                // gRPC 서버 연결
                chatGrpcRepository.connect()

                // 채팅방 참여
                chatGrpcRepository.joinChatRoom(groupId, userId, userName)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "실시간 채팅 연결 실패: ${e.message}"
                )
            }
        }
    }

    // 새 메시지 추가 (gRPC로 실시간 메시지 받았을 때)
    fun addNewMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.chatMessages.toMutableList()

        // 중복 메시지 체크 (messageId 기준)
        if (currentMessages.none { it.messageId == message.messageId }) {
            currentMessages.add(message)
            _uiState.value = _uiState.value.copy(chatMessages = currentMessages)
        }
    }

    // 메시지 전송 - gRPC로 실시간 전송
    fun sendMessage(messageText: String) {
        if (!_uiState.value.grpcConnected) {
            _uiState.value = _uiState.value.copy(
                error = "실시간 채팅에 연결되지 않았습니다."
            )
            return
        }

        // gRPC로 실시간 메시지 전송
        chatGrpcRepository.sendMessage(messageText)
    }

    // 채팅방 나가기
    fun leaveChatRoom() {
        chatGrpcRepository.disconnect()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        chatGrpcRepository.newMessages.removeObserver(grpcMessageObserver)
        // ViewModel 정리 시 gRPC 연결 해제
        chatGrpcRepository.disconnect()
    }
}