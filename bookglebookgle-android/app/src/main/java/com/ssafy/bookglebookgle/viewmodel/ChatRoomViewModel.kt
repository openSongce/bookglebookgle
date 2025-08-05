package com.ssafy.bookglebookgle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.ChatMessagesResponse
import com.ssafy.bookglebookgle.repository.ChatRepositoryImpl
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import com.ssafy.bookglebookgle.util.UserInfoManager
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
    val error: String? = null
)

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepositoryImpl: ChatRepositoryImpl,
    private val groupRepositoryImpl: GroupRepositoryImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    // 메시지가 내가 보낸 것인지 확인하는 함수
    fun isMyMessage(message: ChatMessage, userId: Int): Boolean {
        return message.userId == userId
    }

    // 더미 데이터 생성 함수
    private fun generateDummyMessages(currentUserId: Int): List<ChatMessage> {
        return listOf(
            ChatMessage(
                messageId = 1L,
                userId = 2,
                nickname = "김철수",
                profileImage = null,
                message = "안녕하세요!",
                timestamp = "오후 2:30"
            ),
            ChatMessage(
                messageId = 2L,
                userId = currentUserId, // 내 메시지
                nickname = "나",
                profileImage = null,
                message = "오늘 모임 시작 시간은 오후 7시입니다.",
                timestamp = "오후 2:32"
            ),
            ChatMessage(
                messageId = 3L,
                userId = 3,
                nickname = "이영희",
                profileImage = null,
                message = "저는 참석할게요~",
                timestamp = "오후 2:35"
            ),
            ChatMessage(
                messageId = 4L,
                userId = 2,
                nickname = "김철수",
                profileImage = null,
                message = "네 알겠습니다!",
                timestamp = "오후 2:36"
            ),
            ChatMessage(
                messageId = 5L,
                userId = currentUserId, // 내 메시지
                nickname = "나",
                profileImage = null,
                message = "장소는 강남역 스타벅스로 할까요?",
                timestamp = "오후 2:40"
            ),
            ChatMessage(
                messageId = 6L,
                userId = 3,
                nickname = "이영희",
                profileImage = null,
                message = "좋아요! 그럼 거기서 만나요",
                timestamp = "오후 2:42"
            ),
            ChatMessage(
                messageId = 7L,
                userId = 4,
                nickname = "박민수",
                profileImage = null,
                message = "저도 갈게요! 몇 명 정도 올 예정인가요?",
                timestamp = "오후 2:45"
            ),
            ChatMessage(
                messageId = 8L,
                userId = currentUserId, // 내 메시지
                nickname = "나",
                profileImage = null,
                message = "지금까지 4명이네요! 더 오실 분 있으면 말씀해주세요",
                timestamp = "오후 2:47"
            ),
            ChatMessage(
                messageId = 9L,
                userId = 2,
                nickname = "김철수",
                profileImage = null,
                message = "혹시 늦을 수도 있는데 괜찮을까요? 회사 일이 좀 밀려서요",
                timestamp = "오후 2:50"
            ),
            ChatMessage(
                messageId = 10L,
                userId = 3,
                nickname = "이영희",
                profileImage = null,
                message = "괜찮아요! 천천히 오세요~",
                timestamp = "오후 2:51"
            ),
            ChatMessage(
                messageId = 11L,
                userId = 4,
                nickname = "박민수",
                profileImage = null,
                message = "저도 조금 늦을 것 같아요. 7시 15분쯤 도착할게요!",
                timestamp = "오후 2:53"
            ),
            ChatMessage(
                messageId = 12L,
                userId = currentUserId, // 내 메시지
                nickname = "나",
                profileImage = null,
                message = "네 모두 무리하지 마시고 안전하게 오세요",
                timestamp = "오후 2:55"
            )
        )
    }

    // 채팅방 입장 및 메시지 불러오기
    fun enterChatRoom(groupId: Long, userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {

                // 그룹 상세 정보 가져와서 제목 설정
                val groupDetail = groupRepositoryImpl.getGroupDetail(groupId)
                val groupTitle = groupDetail.body()?.roomTitle

                // TODO: 나중에 실제 API 호출로 교체
                // val chatMessagesResponse = chatRepositoryImpl.getChatMessages(groupId)

                // 현재는 더미 데이터 사용
                val dummyMessages = generateDummyMessages(userId)
                val dummyChatRoom = ChatMessagesResponse(
                    groupId = groupId,
                    messages = dummyMessages
                )

                _uiState.value = _uiState.value.copy(
                    currentChatRoom = dummyChatRoom,
                    chatMessages = dummyMessages,
                    groupTitle = groupTitle?: "채팅방",
                    isLoading = false
                )

                // Todo: 실제 API 사용할 때는 아래 코드 주석 해제
                /*
                val chatMessagesResponse = chatRepositoryImpl.getChatMessages(groupId)
                _uiState.value = _uiState.value.copy(
                    currentChatRoom = chatMessagesResponse,
                    chatMessages = chatMessagesResponse.messages,
                    groupTitle = groupTitle?: "채팅방",
                    isLoading = false
                )
                */

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

    // 메시지 전송 (임시 - gRPC 연결 전까지)
    fun sendMessage(messageText: String, currentUserId: Int, userInfo: ChatMessage?) {
        val newMessage = ChatMessage(
            messageId = System.currentTimeMillis(),
            userId = currentUserId,
            nickname = userInfo?.nickname ?: "사용자",
            profileImage = userInfo?.profileImage,
            message = messageText,
            timestamp = "방금 전"
        )

        addNewMessage(newMessage)

        // TODO: 나중에 gRPC로 실제 메시지 전송
        // grpcClient.sendMessage(messageText)
    }

    // 에러 초기화
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}