package com.ssafy.bookglebookgle.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.MessageType
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
    val grpcConnected: Boolean = false,
    val shouldScrollToBottom: Boolean = false,
    val isMarkingAsRead: Boolean = false,
    // AI 토론 관련 상태
    val isDiscussionActive: Boolean = false,
    val currentAiResponse: String? = null,
    val suggestedTopics: List<String> = emptyList(),
    val showAiSuggestions: Boolean = false,
    val isDiscussionConnecting: Boolean = false,
    // 카테고리 관련 상태 추가
    val isReadingCategory: Boolean = false,
)

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepositoryImpl: ChatRepositoryImpl, // REST API
    private val groupRepositoryImpl: GroupRepositoryImpl,
    private val chatGrpcRepository: ChatGrpcRepository // gRPC
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    // 메시지 타입별 Observer들
    private val grpcMessageObserver = Observer<ChatMessage> { newMessage ->
        addNewMessage(newMessage)
    }
    private val aiResponseObserver = Observer<ChatMessage> { aiMessage ->
        handleAiResponse(aiMessage)
    }
    private val discussionStatusObserver = Observer<ChatMessage> { statusMessage ->
        handleDiscussionStatus(statusMessage)
    }

    private var currentGroupId: Long = 0
    private var currentUserId: Long = 0
    private var isInitialLoad = true

    init {
        // 각 타입별 Observer 등록
        chatGrpcRepository.newMessages.observeForever(grpcMessageObserver)
        chatGrpcRepository.aiResponses.observeForever(aiResponseObserver)
        chatGrpcRepository.discussionStatus.observeForever(discussionStatusObserver)
        observeGrpcConnection()
    }

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

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterChatRoom(groupId: Long, userId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentGroupId = groupId
            currentUserId = userId

            try {
                val groupDetail = groupRepositoryImpl.getGroupDetail(groupId)
                val groupTitle = groupDetail.body()?.roomTitle
                val groupCategory = groupDetail.body()?.category

                // 카테고리가 READING인지 확인
                val isReadingCategory = groupCategory?.equals("READING", ignoreCase = true) == true

                val latestMessages = chatRepositoryImpl.getLatestChatMessages(groupId, 15)

                val chatMessages = latestMessages.map { response ->
                    ChatMessage(
                        messageId = response.id,
                        userId = response.userId,
                        nickname = response.userNickname,
                        profileImage = null,
                        message = response.message,
                        timestamp = DateTimeUtils.formatChatTime(response.createdAt),
                        type = MessageType.NORMAL // 기존 메시지는 NORMAL로 처리
                    )
                }.sortedBy { it.messageId }

                _uiState.value = _uiState.value.copy(
                    chatMessages = chatMessages,
                    groupTitle = groupTitle ?: "채팅방",
                    isLoading = false,
                    hasMoreData = latestMessages.size >= 15,
                    shouldScrollToBottom = true,
                    isReadingCategory = isReadingCategory // 카테고리 상태 설정
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadMoreMessages() {
        if (_uiState.value.isLoadingMore ||
            !_uiState.value.hasMoreData ||
            isInitialLoad ||
            _uiState.value.isLoading) {
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

                val oldestMessageId = currentMessages.firstOrNull()?.messageId ?: run {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    return@launch
                }

                val olderMessages = chatRepositoryImpl.getOlderChatMessages(
                    roomId = currentGroupId,
                    beforeMessageId = oldestMessageId,
                    15
                )

                if (olderMessages.isNotEmpty()) {
                    val newChatMessages = olderMessages.map { response ->
                        ChatMessage(
                            messageId = response.id,
                            userId = response.userId,
                            nickname = response.userNickname,
                            profileImage = null,
                            message = response.message,
                            timestamp = DateTimeUtils.formatChatTime(response.createdAt),
                            type = MessageType.NORMAL
                        )
                    }.sortedBy { it.messageId }

                    // 메시지 순서 보장 - 이전 메시지를 앞에, 기존 메시지를 뒤에
                    val updatedMessages = newChatMessages + currentMessages

                    _uiState.value = _uiState.value.copy(
                        chatMessages = updatedMessages,
                        isLoadingMore = false,
                        hasMoreData = olderMessages.size >= 15
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
                val userName = "사용자 $userId"

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

    // 일반 메시지 추가
    fun addNewMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.chatMessages.toMutableList()

        // 중복 메시지 체크 (messageId 기준)
        if (currentMessages.none { it.messageId == message.messageId }) {
            currentMessages.add(message)
            val sortedMessages = currentMessages.sortedBy { it.messageId }
            _uiState.value = _uiState.value.copy(chatMessages = sortedMessages)

            // 새 메시지 도착 시 읽음 처리 (본인 메시지가 아닌 경우)
            if (message.userId != currentUserId) {
                markChatAsRead()
            }
        }
    }

    // AI 응답 처리
    private fun handleAiResponse(aiMessage: ChatMessage) {
        // AI 응답도 채팅 메시지로 추가
        addNewMessage(aiMessage)

        // AI 응답의 추가 정보를 상태에 반영
        _uiState.value = _uiState.value.copy(
            currentAiResponse = aiMessage.aiResponse,
            suggestedTopics = aiMessage.suggestedTopics,
            showAiSuggestions = aiMessage.suggestedTopics.isNotEmpty()
        )
    }

    // 토론 상태 변화 처리
    private fun handleDiscussionStatus(statusMessage: ChatMessage) {
        // READING 카테고리가 아닌 경우 토론 상태 메시지 무시
        if (!_uiState.value.isReadingCategory) {
            return
        }

        // 상태 메시지도 채팅에 표시
        addNewMessage(statusMessage)

        // 토론 상태 업데이트
        val isActive = statusMessage.type == MessageType.DISCUSSION_START
        _uiState.value = _uiState.value.copy(
            isDiscussionActive = isActive,
            isDiscussionConnecting = false,
            // 토론 종료 시 AI 관련 상태 초기화
            showAiSuggestions = if (!isActive) false else _uiState.value.showAiSuggestions,
            currentAiResponse = if (!isActive) null else _uiState.value.currentAiResponse,
            suggestedTopics = if (!isActive) emptyList() else _uiState.value.suggestedTopics
        )
    }

    /**
     * 채팅방 읽음 처리
     * */
    fun markChatAsRead() {
        if (_uiState.value.isMarkingAsRead || currentGroupId == 0L) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMarkingAsRead = true)

            try {
                val response = chatRepositoryImpl.markChatAsRead(currentGroupId)
                if (response.isSuccessful) {
                    // 읽음 처리 성공 - 특별한 UI 업데이트가 필요하다면 여기서 처리
                    // 예: 읽지 않은 메시지 카운트 업데이트 등
                } else {
                    // 읽음 처리 실패 시 에러 표시 여부는 UX에 따라 결정
                    // 일반적으로 읽음 처리 실패는 사용자에게 알리지 않음
                }

            } catch (e: Exception) {
                // 네트워크 에러 등은 로그만 남기고 사용자에게는 알리지 않음
                // Log.e(TAG, "채팅방 읽음 처리 실패", e)
            } finally {
                _uiState.value = _uiState.value.copy(isMarkingAsRead = false)
            }
        }
    }

    // 일반 메시지 전송
    fun sendMessage(messageText: String) {
        if (!_uiState.value.grpcConnected) {
            _uiState.value = _uiState.value.copy(
                error = "실시간 채팅에 연결되지 않았습니다."
            )
            return
        }

        chatGrpcRepository.sendMessage(messageText)
    }

    // 토론 시작
    fun startDiscussion() {
        // READING 카테고리가 아닌 경우 토론 시작 불가
        if (!_uiState.value.isReadingCategory) {
            _uiState.value = _uiState.value.copy(
                error = "도서 카테고리 채팅방에서만 토론 기능을 사용할 수 있습니다."
            )
            return
        }

        if (!_uiState.value.grpcConnected) {
            _uiState.value = _uiState.value.copy(
                error = "실시간 채팅에 연결되지 않았습니다."
            )
            return
        }

        viewModelScope.launch {
            try {
                // 토론 연결 시작 - 로딩 상태 활성화
                _uiState.value = _uiState.value.copy(
                    isDiscussionConnecting = true,
                    error = null
                )

                // 토론 시작 요청
                chatGrpcRepository.startDiscussion("AI 토론을 시작합니다. 자유롭게 의견을 나누어 보세요!")

                // 실제로는 gRPC 응답을 받으면 handleDiscussionStatus에서 로딩이 해제됩니다
                // 만약 타임아웃이 필요하다면 여기서 설정할 수 있습니다

            } catch (e: Exception) {
                // 에러 발생 시 로딩 해제
                _uiState.value = _uiState.value.copy(
                    isDiscussionConnecting = false,
                    error = "토론 시작 실패: ${e.message}"
                )
            }
        }
    }

    // 토론 종료
    fun endDiscussion() {
        // READING 카테고리가 아닌 경우 토론 종료 불가
        if (!_uiState.value.isReadingCategory) {
            return
        }

        if (!_uiState.value.grpcConnected) {
            return
        }

        viewModelScope.launch {
            try {
                chatGrpcRepository.endDiscussion("AI 토론을 종료합니다. 수고하셨습니다!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "토론 종료 실패: ${e.message}"
                )
            }
        }
    }

    // AI 추천 주제 선택
    fun selectSuggestedTopic(topic: String) {
        if (!_uiState.value.isReadingCategory) {
            return
        }

        sendMessage(topic)
        // 선택 후 추천 주제 숨기기
        _uiState.value = _uiState.value.copy(showAiSuggestions = false)
    }

    // AI 추천 주제 패널 닫기
    fun dismissAiSuggestions() {
        _uiState.value = _uiState.value.copy(showAiSuggestions = false)
    }

    // 채팅방 나가기
    fun leaveChatRoom() {
        markChatAsRead()
        // READING 카테고리인 경우에만 토론 종료
        if (_uiState.value.isReadingCategory) {
            endDiscussion()
        }
        chatGrpcRepository.disconnect()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        chatGrpcRepository.newMessages.removeObserver(grpcMessageObserver)
        chatGrpcRepository.aiResponses.removeObserver(aiResponseObserver)
        chatGrpcRepository.discussionStatus.removeObserver(discussionStatusObserver)
        chatGrpcRepository.disconnect()
    }

    fun resetScrollFlag() {
        _uiState.value = _uiState.value.copy(shouldScrollToBottom = false)
    }
}