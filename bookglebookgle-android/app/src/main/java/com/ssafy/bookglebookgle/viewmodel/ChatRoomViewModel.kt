package com.ssafy.bookglebookgle.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import com.ssafy.bookglebookgle.repository.ChatRepositoryImpl
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import com.ssafy.bookglebookgle.repository.ChatGrpcRepository
import com.ssafy.bookglebookgle.repository.GrpcConnectionStatus
import com.ssafy.bookglebookgle.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatRoomUiState(
    val chatMessages: List<ChatMessage> = emptyList(),
    val groupTitle: String = "채팅방",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreData: Boolean = true,
    val error: String? = null,
    val grpcConnected: Boolean = false
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

    private var currentGroupId: Long = 0 // 현재 채팅방 ID 저장
    private var isInitialLoad = true     // 최초 로드 여부

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

    fun isMyMessage(message: ChatMessage, userId: Long): Boolean {
        return message.userId == userId
    }

    // 채팅방 입장 - REST API + gRPC 연결
    @RequiresApi(Build.VERSION_CODES.O)
    fun enterChatRoom(groupId: Long, userId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentGroupId = groupId

            try {
                // 그룹 정보 불러오기
                val groupDetail = groupRepositoryImpl.getGroupDetail(groupId)
                val groupTitle = groupDetail.body()?.roomTitle

                // 최신 메시지 불러오기
                val latestMessages = chatRepositoryImpl.getLatestChatMessages(groupId, 20)

                val chatMessages = latestMessages.map { response ->
                    ChatMessage(
                        messageId = response.id,
                        userId = response.userId,
                        nickname = response.userNickname,
                        profileImage = null,
                        message = response.message,
                        timestamp = DateTimeUtils.formatChatTime(response.createdAt)
                    )
                }.sortedBy { it.messageId } // 메시지 ID 기준 오름차순 정렬

                _uiState.value = _uiState.value.copy(
                    chatMessages = chatMessages,
                    groupTitle = groupTitle ?: "채팅방",
                    isLoading = false,
                    hasMoreData = latestMessages.size >= 20
                )

                isInitialLoad = false

                //gRPC 연결
                connectToGrpcChat(groupId, userId)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "채팅방 불러오기 실패: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    // 이전 메시지 불러오기 (스크롤 페이징)
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadMoreMessages() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreData || isInitialLoad) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)

            try {
                val currentMessages = _uiState.value.chatMessages
                if (currentMessages.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    return@launch
                }

                val oldestMessageId = currentMessages.firstOrNull()?.messageId ?: return@launch

                // 이전 메시지들 불러오기
                val olderMessages = chatRepositoryImpl.getOlderChatMessages(
                    roomId = currentGroupId,
                    beforeMessageId = oldestMessageId,
                    20
                )

                if (olderMessages.isNotEmpty()) {
                    val newChatMessages = olderMessages.map { response ->
                        ChatMessage(
                            messageId = response.id,
                            userId = response.userId,
                            nickname = response.userNickname,
                            profileImage = null,
                            message = response.message,
                            timestamp = DateTimeUtils.formatChatTime(response.createdAt)
                        )
                    }.sortedBy { it.messageId }

                    // 기존 메시지 앞에 이전 메시지들 추가
                    val updatedMessages = newChatMessages + currentMessages

                    _uiState.value = _uiState.value.copy(
                        chatMessages = updatedMessages,
                        isLoadingMore = false,
                        hasMoreData = olderMessages.size >= 20
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        hasMoreData = false
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "이전 메시지 불러오기 실패: ${e.message}",
                    isLoadingMore = false
                )
            }
        }
    }


    // gRPC 실시간 채팅 연결
    private fun connectToGrpcChat(groupId: Long, userId: Long) {
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
            val sortedMessages = currentMessages.sortedBy { it.messageId }
            _uiState.value = _uiState.value.copy(chatMessages = sortedMessages)
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