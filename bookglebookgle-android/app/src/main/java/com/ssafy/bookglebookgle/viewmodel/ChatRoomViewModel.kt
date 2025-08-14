package com.ssafy.bookglebookgle.viewmodel

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.MessageType
import com.ssafy.bookglebookgle.entity.QuizPhase
import com.ssafy.bookglebookgle.entity.QuizQuestion
import com.ssafy.bookglebookgle.entity.QuizReveal
import com.ssafy.bookglebookgle.entity.QuizSummary
import com.ssafy.bookglebookgle.repository.ChatRepositoryImpl
import com.ssafy.bookglebookgle.repository.GroupRepositoryImpl
import com.ssafy.bookglebookgle.repository.ChatGrpcRepository
import com.ssafy.bookglebookgle.repository.GrpcConnectionStatus
import com.ssafy.bookglebookgle.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "싸피_ChatRoomViewModel"
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
    val isStudyCategory: Boolean = false,
    val isAiTyping: Boolean = false,
    // 모임장 여부 추가
    val isHost: Boolean = false,
    // 토론 상태 자동 감지 관련
    val isDiscussionAutoDetected: Boolean = false,
    // 퀴즈 관련 상태 추가
    val isQuizActive: Boolean = false,
    val currentQuizId: String? = null,
    val currentQuestion: QuizQuestion? = null,
    val selectedAnswerIndex: Int? = null,
    val isAnswerSubmitted: Boolean = false,
    val quizTimeRemaining: Int = 0,
    val showQuizResult: Boolean = false,
    val currentQuizReveal: QuizReveal? = null,
    val quizSummary: QuizSummary? = null,
    val isQuizConnecting: Boolean = false,
    val userQuizAnswers: Map<Int, Int> = emptyMap(), // questionIndex -> selectedIndex
    // 진도율 관련 상태 추가
    val averageProgress: Int = 0,
    val isLoadingProgress: Boolean = false,
    val progressError: String? = null,
    )

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val chatRepositoryImpl: ChatRepositoryImpl, // REST API
    private val groupRepositoryImpl: GroupRepositoryImpl,
    private val chatGrpcRepository: ChatGrpcRepository // gRPC
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    // 시스템 메시지 ID 생성을 위한 카운터 추가
    private var systemMessageIdCounter = 0L

    // 고유한 시스템 메시지 ID 생성
    private fun generateSystemMessageId(): Long {
        val currentTime = System.currentTimeMillis()
        return currentTime + (++systemMessageIdCounter)
    }

    // 메시지 타입별 Observer들
    private val grpcMessageObserver = Observer<ChatMessage> { newMessage ->
        addNewMessage(newMessage)
    }
    private val aiResponseObserver = Observer<ChatMessage> { aiMessage ->
        Log.d(TAG, "=== AI 응답 메시지 수신 ===")
        Log.d(TAG, "messageId: ${aiMessage.messageId}")
        Log.d(TAG, "userId: ${aiMessage.userId}")
        Log.d(TAG, "nickname: ${aiMessage.nickname}")
        Log.d(TAG, "message: ${aiMessage.message}")
        Log.d(TAG, "timestamp: ${aiMessage.timestamp}")
        Log.d(TAG, "type: ${aiMessage.type}")
        Log.d(TAG, "aiResponse: ${aiMessage.aiResponse}")
        Log.d(TAG, "suggestedTopics: ${aiMessage.suggestedTopics}")
        Log.d(TAG, "============================")
        handleAiResponse(aiMessage)
    }
    private val discussionStatusObserver = Observer<ChatMessage> { statusMessage ->
        handleDiscussionStatus(statusMessage)
    }

    private val quizMessageObserver = Observer<ChatMessage> { quizMessage ->
        Log.d(TAG, "=== 퀴즈 메시지 수신 ===")
        Log.d(TAG, "messageId: ${quizMessage.messageId}")
        Log.d(TAG, "userId: ${quizMessage.userId}")
        Log.d(TAG, "nickname: ${quizMessage.nickname}")
        Log.d(TAG, "message: ${quizMessage.message}")
        Log.d(TAG, "timestamp: ${quizMessage.timestamp}")
        Log.d(TAG, "type: ${quizMessage.type}")
        Log.d(TAG, "quizStart: ${quizMessage.quizStart}")
        Log.d(TAG, "quizQuestion: ${quizMessage.quizQuestion}")
        Log.d(TAG, "quizReveal: ${quizMessage.quizReveal}")
        Log.d(TAG, "quizSummary: ${quizMessage.quizSummary}")
        Log.d(TAG, "============================")
        handleQuizMessage(quizMessage)
    }

    private var currentGroupId: Long = 0
    private var currentUserId: Long = 0
    private var isInitialLoad = true
    private var userName: String = ""

    // AI 타이핑 타임아웃 관련 변수 추가
    private var aiTypingTimeoutJob: Job? = null
    private val AI_TYPING_TIMEOUT_SECONDS = 30

    // 퀴즈 관련 변수들
    private var quizTimerJob: Job? = null

    // 퀴즈 연결 타임아웃 관련 변수 추가
    private var quizConnectionTimeoutJob: Job? = null
    private val QUIZ_CONNECTION_TIMEOUT_SECONDS = 15


    init {
        // 각 타입별 Observer 등록
        chatGrpcRepository.newMessages.observeForever(grpcMessageObserver)
        chatGrpcRepository.aiResponses.observeForever(aiResponseObserver)
        chatGrpcRepository.discussionStatus.observeForever(discussionStatusObserver)
        chatGrpcRepository.quizMessages.observeForever(quizMessageObserver)
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
                val groupTitle = groupDetail.roomTitle
                val groupCategory = groupDetail.category
                val isHost = groupDetail.isHost ?: false

                // 카테고리가 READING인지 확인
                val isReadingCategory = groupCategory?.equals("READING", ignoreCase = true) == true
                val isStudyCategory = groupCategory?.equals("STUDY", ignoreCase = true) == true

                val latestMessages = chatRepositoryImpl.getLatestChatMessages(groupId, 15)

                // 실제 사용자 닉네임 저장
                var actualUserName = "사용자"

                val chatMessages = latestMessages.map { response ->
                    // 현재 사용자의 메시지에서 실제 닉네임 추출
                    if (response.userId == userId) {
                        actualUserName = response.userNickname
                    }

                    ChatMessage(
                        messageId = response.id,
                        userId = response.userId,
                        nickname = response.userNickname,
                        profileImage = response.profileImgUrl,
                        message = response.message,
                        timestamp = DateTimeUtils.formatChatTime(response.createdAt),
                        type = MessageType.NORMAL, // 기존 메시지는 NORMAL로 처리
                        avatarBgColor = response.profileColor
                    )
                }.sortedBy { it.messageId }

                // userName에 실제 닉네임 저장
                userName = actualUserName

                // AI 응답 메시지 감지를 통한 토론 상태 자동 감지
                val hasRecentAiResponse = chatMessages.any { message ->
                    message.type == MessageType.AI_RESPONSE
                }

                // 퀴즈 상태 자동 감지
                val hasActiveQuiz = chatMessages.any { message ->
                    message.type in listOf(MessageType.QUIZ_START, MessageType.QUIZ_QUESTION) &&
                            message.type != MessageType.QUIZ_END
                }

                // READING 카테고리이고 최근 AI 응답이 있으면 토론 중으로 간주
                val autoDetectedDiscussion = isReadingCategory && hasRecentAiResponse

                _uiState.value = _uiState.value.copy(
                    chatMessages = chatMessages,
                    groupTitle = groupTitle ?: "채팅방",
                    isLoading = false,
                    hasMoreData = latestMessages.size >= 15,
                    shouldScrollToBottom = true,
                    isReadingCategory = isReadingCategory, // 카테고리 상태 설정
                    isStudyCategory = isStudyCategory,
                    isHost = isHost,
                    // AI 응답이 있으면 토론 중으로 자동 설정
                    isDiscussionActive = autoDetectedDiscussion,
                    // 자동 감지되었는지 플래그 설정
                    isDiscussionAutoDetected = autoDetectedDiscussion,
                    isQuizActive = hasActiveQuiz
                )

                isInitialLoad = false

                // STUDY 카테고리인 경우에만 진도율 조회
                if (isStudyCategory) {
                    loadGroupProgress()
                }

                //gRPC 연결
                connectToGrpcChat(groupId, userId, actualUserName)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "채팅방 불러오기 실패: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    // 퀴즈 시작 함수들 추가
    @RequiresApi(Build.VERSION_CODES.O)
    fun startMidtermQuiz() {
        startQuizWithPhase(QuizPhase.MIDTERM, "중간 퀴즈")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startFinalQuiz() {
        startQuizWithPhase(QuizPhase.FINAL, "최종 퀴즈")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startQuizWithPhase(phase: QuizPhase, quizName: String) {
        if (!_uiState.value.isHost) {
            _uiState.value = _uiState.value.copy(
                error = "$quizName 시작은 모임장만 가능합니다.",
                isQuizConnecting = false
            )
            return
        }

        if (!_uiState.value.isStudyCategory) {
            _uiState.value = _uiState.value.copy(
                error = "학습 카테고리 채팅방에서만 퀴즈 기능을 사용할 수 있습니다.",
                isQuizConnecting = false
            )
            return
        }

        if (!_uiState.value.grpcConnected) {
            _uiState.value = _uiState.value.copy(
                error = "실시간 채팅에 연결되지 않았습니다.",
                isQuizConnecting = false
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isQuizConnecting = true,
                    error = null
                )

                // 퀴즈 연결 타임아웃 타이머 시작
                startQuizConnectionTimeout()

                // 진도율에 따른 프로그레스 퍼센티지 설정
                val progressPercentage = when (phase) {
                    QuizPhase.MIDTERM -> 50
                    QuizPhase.FINAL -> 100
                    else -> _uiState.value.averageProgress
                }

                // 퀴즈 시작 요청
                chatGrpcRepository.startQuiz(
                    groupId = currentGroupId,
                    meetingId = currentGroupId.toString(),
                    documentId = currentGroupId.toString(),
                    phase = phase,
                    progressPercentage = progressPercentage
                )

            } catch (e: Exception) {
                // 에러 발생 시 타임아웃 타이머 정지 및 로딩 해제
                stopQuizConnectionTimeout()
                _uiState.value = _uiState.value.copy(
                    isQuizConnecting = false,
                    error = "$quizName 시작 요청 실패: ${e.message}"
                )

                // 퀴즈 시작 실패 시스템 메시지 추가
                val currentTime = System.currentTimeMillis()
                val errorMessage = ChatMessage(
                    messageId = currentTime,
                    userId = -1,
                    nickname = "시스템",
                    profileImage = null,
                    message = "$quizName 시작에 실패했습니다: ${e.message}",
                    timestamp = DateTimeUtils.formatChatTime(currentTime.toString()),
                    type = MessageType.QUIZ_END
                )
                addNewMessage(errorMessage)
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
                            profileImage = response.profileImgUrl,
                            message = response.message,
                            timestamp = DateTimeUtils.formatChatTime(response.createdAt),
                            type = MessageType.NORMAL,
                            avatarBgColor = response.profileColor
                        )
                    }.sortedBy { it.messageId }

                    // 메시지 순서 보장 - 이전 메시지를 앞에, 기존 메시지를 뒤에
                    val updatedMessages = newChatMessages + currentMessages

                    // 더 불러온 메시지들을 포함해서 AI 응답 재검사
                    val hasAiResponseInAllMessages = updatedMessages.any { message ->
                        message.type == MessageType.AI_RESPONSE
                    }

                    // READING 카테고리이고 AI 응답이 있으면 토론 중으로 자동 설정
                    val autoDetectedDiscussion = _uiState.value.isReadingCategory && hasAiResponseInAllMessages

                    _uiState.value = _uiState.value.copy(
                        chatMessages = updatedMessages,
                        isLoadingMore = false,
                        hasMoreData = olderMessages.size >= 15,
                        // 이전 메시지에서도 AI 응답 감지 시 토론 상태 업데이트
                        isDiscussionActive = autoDetectedDiscussion || _uiState.value.isDiscussionActive

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

    /**
     * 모임 멤버들의 진도율 조회 및 평균 계산
     */
    private fun loadGroupProgress() {
        if (currentGroupId == 0L) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProgress = true, progressError = null)

            try {
                val response = groupRepositoryImpl.getGroupMembersProgress(currentGroupId)

                if (response.isSuccessful) {
                    val memberProgressList = response.body() ?: emptyList()

                    if (memberProgressList.isNotEmpty()) {
                        // 평균 진도율 계산
                        val totalProgress = memberProgressList.sumOf { it.progressPercent }
                        val averageProgress = totalProgress / memberProgressList.size

                        Log.d(TAG, "모임 진도율 조회 성공 - 멤버수: ${memberProgressList.size}, 평균 진도율: $averageProgress%")

                        _uiState.value = _uiState.value.copy(
                            averageProgress = averageProgress,
                            isLoadingProgress = false
                        )
                    } else {
                        Log.d(TAG, "모임 멤버 진도율 데이터가 없음")
                        _uiState.value = _uiState.value.copy(
                            averageProgress = 0,
                            isLoadingProgress = false
                        )
                    }
                } else {
                    val errorMsg = "진도율 조회 실패: ${response.message()}"
                    Log.d(TAG, errorMsg)
                    _uiState.value = _uiState.value.copy(
                        isLoadingProgress = false,
                        progressError = errorMsg
                    )
                }
            } catch (e: Exception) {
                val errorMsg = "진도율 조회 중 오류: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _uiState.value = _uiState.value.copy(
                    isLoadingProgress = false,
                    progressError = errorMsg
                )
            }
        }
    }

    // gRPC 실시간 채팅 연결
    private fun connectToGrpcChat(groupId: Long, userId: Long, actualUserName: String) {
        viewModelScope.launch {
            try {

                // gRPC 서버 연결
                chatGrpcRepository.connect()

                // 채팅방 참여
                chatGrpcRepository.joinChatRoom(groupId, userId, actualUserName)

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
            // AI 응답 메시지가 추가되면 토론 상태 자동 활성화
            val shouldActivateDiscussion = _uiState.value.isReadingCategory &&
                    message.type == MessageType.AI_RESPONSE

            _uiState.value = _uiState.value.copy(
                chatMessages = sortedMessages,
                // AI 응답이 추가되면 토론 중으로 자동 설정
                isDiscussionActive = shouldActivateDiscussion || _uiState.value.isDiscussionActive
            )

            // 토론 중이고 일반 메시지(사용자 메시지)가 추가된 경우 AI 타이핑 시작
            if (_uiState.value.isReadingCategory &&
                _uiState.value.isDiscussionActive &&
                message.type == MessageType.NORMAL) {
                setAiTyping(true)
            }

            // 새 메시지 도착 시 읽음 처리 (본인 메시지가 아닌 경우)
            if (message.userId != currentUserId) {
                markChatAsRead()
            }
        }
    }

    // AI 응답 처리
    private fun handleAiResponse(aiMessage: ChatMessage) {
        // AI 응답이 도착하면 타이핑 상태 해제
        setAiTyping(false)

        //  토론 연결 로딩 해제
        if (_uiState.value.isDiscussionConnecting) {
            _uiState.value = _uiState.value.copy(isDiscussionConnecting = false)
        }

        // AI 응답도 채팅 메시지로 추가
        addNewMessage(aiMessage)

        // AI 응답의 추가 정보를 상태에 반영
        _uiState.value = _uiState.value.copy(
            currentAiResponse = aiMessage.aiResponse,
            suggestedTopics = aiMessage.suggestedTopics,
            showAiSuggestions = aiMessage.suggestedTopics.isNotEmpty(),
            // AI 응답이 오면 토론 중으로 자동 설정 (READING 카테고리인 경우)
            isDiscussionActive = if (_uiState.value.isReadingCategory) true else _uiState.value.isDiscussionActive,
            shouldScrollToBottom = true
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
            suggestedTopics = if (!isActive) emptyList() else _uiState.value.suggestedTopics,

            // 토론 종료 시 AI 타이핑 상태도 초기화
            isAiTyping = if (!isActive) false else _uiState.value.isAiTyping,
            shouldScrollToBottom = true
        )

        // 토론 종료 시 AI 타이핑도 명시적으로 정지
        if (!isActive) {
            setAiTyping(false)
        }
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

        // 토론이 활성화된 상태라면 AI 타이핑 시작
        if (_uiState.value.isReadingCategory && _uiState.value.isDiscussionActive) {
            setAiTyping(true)
        }
    }

    // 토론 시작
    fun startDiscussion() {
        // 모임장 권한 검사 추가
        if (!_uiState.value.isHost) {
            _uiState.value = _uiState.value.copy(
                error = "토론 시작은 모임장만 가능합니다."
            )
            return
        }

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
                )
            }
        }
    }

    // 토론 종료
    fun endDiscussion() {
        // 모임장 권한 검사 추가
        if (!_uiState.value.isHost) {
            _uiState.value = _uiState.value.copy(
                error = "토론 종료는 모임장만 가능합니다."
            )
            return
        }

        // READING 카테고리가 아닌 경우 토론 종료 불가
        if (!_uiState.value.isReadingCategory) {
            return
        }

        if (!_uiState.value.grpcConnected) {
            return
        }

        // 이미 토론이 종료된 상태인 경우
        if (!_uiState.value.isDiscussionActive) {
            return
        }

        viewModelScope.launch {
            try {
                chatGrpcRepository.endDiscussion("AI 토론을 종료합니다. 수고하셨습니다!")

                // 토론 종료 시 즉시 로컬 상태 업데이트
                _uiState.value = _uiState.value.copy(
                    isDiscussionActive = false,
                    isDiscussionAutoDetected = false,
                    showAiSuggestions = false,
                    currentAiResponse = null,
                    suggestedTopics = emptyList(),
                    isAiTyping = false,
                    isDiscussionConnecting = false
                )

                // 토론 종료 시 AI 타이핑 상태도 해제
                setAiTyping(false)

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

    // AI 타이핑 상태 설정
    fun setAiTyping(isTyping: Boolean) {
        // READING 카테고리이고 토론이 활성화된 상태에서만 AI 타이핑 표시
        val shouldShowTyping = _uiState.value.isReadingCategory &&
                _uiState.value.isDiscussionActive &&
                isTyping

        _uiState.value = _uiState.value.copy(isAiTyping = shouldShowTyping)

        if (shouldShowTyping) {
            // AI 타이핑 시작 시 타임아웃 타이머 시작
            startAiTypingTimeout()
        } else {
            // AI 타이핑 종료 시 타임아웃 타이머 정지
            stopAiTypingTimeout()
        }
    }

    // AI 타이핑 타임아웃 타이머 시작
    private fun startAiTypingTimeout() {
        stopAiTypingTimeout() // 기존 타이머 정지

        aiTypingTimeoutJob = viewModelScope.launch {
            delay(AI_TYPING_TIMEOUT_SECONDS * 1000L) // 10초 대기

            // 타임아웃 발생 시 처리
            if (_uiState.value.isAiTyping) {
                handleAiTypingTimeout()
            }
        }
    }

    // AI 타이핑 타임아웃 처리
    @SuppressLint("NewApi")
    private fun handleAiTypingTimeout() {
        // AI 타이핑 상태 해제
        _uiState.value = _uiState.value.copy(isAiTyping = false)

        // 타임아웃 메시지를 AI 응답 형태로 생성
        val currentTime = System.currentTimeMillis()
        val timeoutMessage = ChatMessage(
            messageId = currentTime, // 임시 ID
            userId = -1, // AI 시스템 메시지용 특별 ID
            nickname = "AI",
            profileImage = null,
            message = "AI 응답 연결 타임아웃",
            timestamp = DateTimeUtils.formatChatTime(currentTime.toString()),
            type = MessageType.AI_RESPONSE,
            aiResponse = "죄송합니다. AI 서버 연결에 문제가 발생했습니다.\n잠시 후 다시 시도해 주세요.",
            suggestedTopics = emptyList()
        )

        // 타임아웃 메시지를 채팅에 추가
        addNewMessage(timeoutMessage)

        // 토론 상태 종료
        _uiState.value = _uiState.value.copy(
            isDiscussionActive = false,
            isDiscussionAutoDetected = false,
            currentAiResponse = null,
            suggestedTopics = emptyList(),
            showAiSuggestions = false
        )

        // 토론 종료 시스템 메시지도 추가
        val discussionEndMessage = ChatMessage(
            messageId = currentTime + 1, // 임시 ID
            userId = -1,
            nickname = "시스템",
            profileImage = null,
            message = "AI 연결 문제로 토론이 자동 종료되었습니다.",
            timestamp = DateTimeUtils.formatChatTime(currentTime.toString()),
            type = MessageType.DISCUSSION_END
        )

        addNewMessage(discussionEndMessage)
    }

    // AI 타이핑 타임아웃 타이머 정지
    private fun stopAiTypingTimeout() {
        aiTypingTimeoutJob?.cancel()
        aiTypingTimeoutJob = null
    }

    // AI가 응답을 시작할 때 호출 (gRPC에서 AI 응답 시작 신호를 받았을 때)
    fun onAiResponseStarted() {
        setAiTyping(true)
    }

    // AI가 응답을 완료했을 때 호출 (gRPC에서 AI 응답 완료 신호를 받았을 때)
    fun onAiResponseCompleted() {
        setAiTyping(false)
    }

    // 채팅방 나가기
    fun leaveChatRoom() {
        markChatAsRead()
        // AI 타이핑 타임아웃 타이머 정지
        stopAiTypingTimeout()
        // 퀴즈 연결 타임아웃 타이머 정지
        stopQuizConnectionTimeout()
        // READING 카테고리인 경우에만 토론 종료
        if (_uiState.value.isReadingCategory) {
            endDiscussion()
        }
        if (_uiState.value.isStudyCategory && _uiState.value.isQuizActive) {
            endQuiz()
        }
        chatGrpcRepository.disconnect()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    public override fun onCleared() {
        super.onCleared()
        stopQuizTimer()
        stopAiTypingTimeout()
        chatGrpcRepository.newMessages.removeObserver(grpcMessageObserver)
        chatGrpcRepository.aiResponses.removeObserver(aiResponseObserver)
        chatGrpcRepository.discussionStatus.removeObserver(discussionStatusObserver)
        chatGrpcRepository.quizMessages.removeObserver(quizMessageObserver)
        chatGrpcRepository.disconnect()
    }

    fun resetScrollFlag() {
        _uiState.value = _uiState.value.copy(shouldScrollToBottom = false)
    }

    // 퀴즈 메시지 처리
    private fun handleQuizMessage(quizMessage: ChatMessage) {
        Log.d("QuizDebug", "퀴즈 메시지 수신: ${quizMessage.type}, 내용: ${quizMessage.message}")

        // 퀴즈 연결 로딩 상태 해제 (어떤 퀴즈 메시지든 받으면 연결 성공으로 간주)
        if (_uiState.value.isQuizConnecting) {
            stopQuizConnectionTimeout()
            _uiState.value = _uiState.value.copy(isQuizConnecting = false)
        }

        // 퀴즈 메시지도 채팅에 추가
        addNewMessage(quizMessage)

        when (quizMessage.type) {
            MessageType.QUIZ_START -> {
                Log.d("QuizDebug", "퀴즈 시작 처리")
                _uiState.value = _uiState.value.copy(
                    isQuizActive = true,
                    currentQuizId = quizMessage.quizStart?.quizId,
                    isQuizConnecting = false,
                    selectedAnswerIndex = null,
                    isAnswerSubmitted = false,
                    showQuizResult = false,
                    currentQuizReveal = null,
                    quizSummary = null,
                    currentQuestion = null,
                    shouldScrollToBottom = true
                )
            }

            MessageType.QUIZ_QUESTION -> {
                quizMessage.quizQuestion?.let { question ->
                    Log.d("QuizDebug", "퀴즈 문제 수신: ${question.questionText}")
                    _uiState.value = _uiState.value.copy(
                        currentQuestion = question,
                        selectedAnswerIndex = null,
                        isAnswerSubmitted = false,
                        quizTimeRemaining = question.timeoutSeconds,
                        showQuizResult = false,
                        currentQuizReveal = null,
                        quizSummary = null,
                        shouldScrollToBottom = true
                    )
                    startQuizTimer(question.timeoutSeconds)
                }
            }

            MessageType.QUIZ_REVEAL -> {
                quizMessage.quizReveal?.let { reveal ->
                    Log.d("QuizDebug", "퀴즈 정답 공개: 정답=${reveal.correctAnswerIndex}, 참여자=${reveal.userAnswers.size}명")
                    _uiState.value = _uiState.value.copy(
                        currentQuizReveal = null,
                        selectedAnswerIndex = null,
                        isAnswerSubmitted = false,
                        showQuizResult = true,
                        currentQuestion = null,
                        quizSummary = null,
                        shouldScrollToBottom = true
                    )
                    stopQuizTimer()

                }
            }
            MessageType.QUIZ_ANSWER -> {
                // 다른 사용자의 답안 제출 알림
                Log.d("QuizDebug", "답안 제출 알림 수신")
            }

            MessageType.QUIZ_SUMMARY -> {
                quizMessage.quizSummary?.let { summary ->
                    Log.d("QuizDebug", "퀴즈 요약 수신: 총 ${summary.totalQuestions}문제, 참여자 ${summary.scores.size}명")
                    _uiState.value = _uiState.value.copy(
                        quizSummary = summary,
                        showQuizResult = false,
                        currentQuizReveal = null,
                        currentQuestion = null,
                        shouldScrollToBottom = true
                    )
                }
            }

            MessageType.QUIZ_END -> {
                Log.d("QuizDebug", "퀴즈 종료 처리")
                viewModelScope.launch {
                    // QUIZ_SUMMARY가 표시되고 있다면 잠시 대기
                    if (_uiState.value.quizSummary != null) {
                        delay(3000) // 3초 대기
                    }

                    _uiState.value = _uiState.value.copy(
                        isQuizActive = false,
                        currentQuizId = null,
                        currentQuestion = null,
                        selectedAnswerIndex = null,
                        isAnswerSubmitted = false,
                        showQuizResult = false,
                        currentQuizReveal = null,
                        quizTimeRemaining = 0,
                        userQuizAnswers = emptyMap(),
                        isQuizConnecting = false,
                        shouldScrollToBottom = true
                    )
                    stopQuizTimer()
                }
            }

            else -> {
                Log.d("QuizDebug", "기타 퀴즈 메시지: ${quizMessage.type}")
            }
        }
    }

    // 퀴즈 타이머 시작
    private fun startQuizTimer(timeoutSeconds: Int) {
        stopQuizTimer() // 기존 타이머 정지

        quizTimerJob = viewModelScope.launch {
            var remainingTime = timeoutSeconds

            while (remainingTime > 0 && _uiState.value.currentQuestion != null) {
                _uiState.value = _uiState.value.copy(quizTimeRemaining = remainingTime)
                delay(1000)
                remainingTime--
            }

            // 시간 종료시 자동 제출 (답안을 선택했지만 제출하지 않은 경우)
            if (_uiState.value.selectedAnswerIndex != null && !_uiState.value.isAnswerSubmitted) {
                submitQuizAnswer()
            }
        }
    }

    // 퀴즈 타이머 정지
    private fun stopQuizTimer() {
        quizTimerJob?.cancel()
        quizTimerJob = null
    }

    // 퀴즈 연결 타임아웃 타이머 시작
    private fun startQuizConnectionTimeout() {
        stopQuizConnectionTimeout() // 기존 타이머 정지

        quizConnectionTimeoutJob = viewModelScope.launch {
            delay(QUIZ_CONNECTION_TIMEOUT_SECONDS * 1000L) // 15초 대기

            // 타임아웃 발생 시 처리
            if (_uiState.value.isQuizConnecting) {
                handleQuizConnectionTimeout()
            }
        }
    }

    // 퀴즈 연결 타임아웃 처리
    @SuppressLint("NewApi")
    private fun handleQuizConnectionTimeout() {
        Log.d(TAG, "퀴즈 연결 타임아웃 발생")

        // 퀴즈 연결 상태 해제
        _uiState.value = _uiState.value.copy(
            isQuizConnecting = false,
            error = "퀴즈 서버 연결 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."
        )

        // 퀴즈 연결 실패 시스템 메시지 추가
        val currentTime = System.currentTimeMillis()
        val timeoutMessage = ChatMessage(
            messageId = currentTime, // 임시 ID
            userId = -1, // 시스템 메시지용 특별 ID
            nickname = "시스템",
            profileImage = null,
            message = "퀴즈 서버 연결에 실패했습니다. 네트워크 상태를 확인하고 다시 시도해주세요.",
            timestamp = DateTimeUtils.formatChatTime(currentTime.toString()),
            type = MessageType.QUIZ_END // 퀴즈 종료 타입으로 표시
        )

        // 타임아웃 메시지를 채팅에 추가
        addNewMessage(timeoutMessage)
    }

    // 퀴즈 연결 타임아웃 타이머 정지
    private fun stopQuizConnectionTimeout() {
        quizConnectionTimeoutJob?.cancel()
        quizConnectionTimeoutJob = null
    }

    // 퀴즈 시작
    @RequiresApi(Build.VERSION_CODES.O)
    fun startQuiz() {

        if (!_uiState.value.isHost) {
            _uiState.value = _uiState.value.copy(
                error = "퀴즈 시작은 모임장만 가능합니다.",
                isQuizConnecting = false
            )
            return
        }

        if (!_uiState.value.isStudyCategory) {
            _uiState.value = _uiState.value.copy(
                error = "학습 카테고리 채팅방에서만 퀴즈 기능을 사용할 수 있습니다.",
                isQuizConnecting = false
            )
            return
        }

        if (!_uiState.value.grpcConnected) {
            _uiState.value = _uiState.value.copy(
                error = "실시간 채팅에 연결되지 않았습니다.",
                isQuizConnecting = false
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isQuizConnecting = true,
                    error = null
                )

                // 퀴즈 연결 타임아웃 타이머 시작
                startQuizConnectionTimeout()

                // 퀴즈 시작 요청
                chatGrpcRepository.startQuiz(
                    groupId = currentGroupId,
                    meetingId = currentGroupId.toString(),
                    documentId = currentGroupId.toString(),
                    phase = QuizPhase.MIDTERM, // 또는 FINAL
                    progressPercentage = 50
                )

            } catch (e: Exception) {
                // 에러 발생 시 타임아웃 타이머 정지 및 로딩 해제
                stopQuizConnectionTimeout()
                _uiState.value = _uiState.value.copy(
                    isQuizConnecting = false,
                    error = "퀴즈 시작 요청 실패: ${e.message}"
                )

                // 퀴즈 시작 실패 시스템 메시지 추가
                val currentTime = System.currentTimeMillis()
                val errorMessage = ChatMessage(
                    messageId = currentTime,
                    userId = -1,
                    nickname = "시스템",
                    profileImage = null,
                    message = "퀴즈 시작에 실패했습니다: ${e.message}",
                    timestamp = DateTimeUtils.formatChatTime(currentTime.toString()),
                    type = MessageType.QUIZ_END
                )
                addNewMessage(errorMessage)
            }
        }
    }

    // 퀴즈 종료
    fun endQuiz() {
        if (!_uiState.value.isHost) {
            _uiState.value = _uiState.value.copy(
                error = "퀴즈 종료는 모임장만 가능합니다."
            )
            return
        }

        if (!_uiState.value.grpcConnected) {
            return
        }

        viewModelScope.launch {
            try {
//                chatGrpcRepository.endQuiz("퀴즈 종료")

                // 로컬 상태도 즉시 업데이트
                _uiState.value = _uiState.value.copy(
                    isQuizActive = false,
                    currentQuizId = null,
                    currentQuestion = null,
                    selectedAnswerIndex = null,
                    isAnswerSubmitted = false,
                    showQuizResult = false,
                    currentQuizReveal = null,
                    quizSummary = null,
                    quizTimeRemaining = 0,
                    userQuizAnswers = emptyMap(),
                    isQuizConnecting = false
                )

                stopQuizTimer()
                stopQuizConnectionTimeout()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "퀴즈 종료 실패: ${e.message}"
                )
            }
        }
    }

    // 퀴즈 답안 선택
    fun selectQuizAnswer(answerIndex: Int) {
        if (_uiState.value.isAnswerSubmitted) return

        _uiState.value = _uiState.value.copy(selectedAnswerIndex = answerIndex)
    }

    // 퀴즈 답안 제출
    fun submitQuizAnswer() {
        val selectedIndex = _uiState.value.selectedAnswerIndex
        val currentQuestion = _uiState.value.currentQuestion
        val quizId = _uiState.value.currentQuizId

        if (selectedIndex == null || currentQuestion == null || quizId == null) {
            return
        }

        if (_uiState.value.isAnswerSubmitted) {
            return
        }

        viewModelScope.launch {
            try {
                chatGrpcRepository.submitQuizAnswer(
                    quizId = quizId,
                    questionIndex = currentQuestion.questionIndex,
                    selectedIndex = selectedIndex
                )

                // 제출 상태 업데이트
                _uiState.value = _uiState.value.copy(
                    isAnswerSubmitted = true,
                    userQuizAnswers = _uiState.value.userQuizAnswers +
                            (currentQuestion.questionIndex to selectedIndex)
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "답안 제출 실패: ${e.message}"
                )
            }
        }
    }

    // 퀴즈 결과 닫기
    fun dismissQuizResult() {
        _uiState.value = _uiState.value.copy(
            showQuizResult = false,
            currentQuizReveal = null
        )
    }

    // 퀴즈 요약 닫기
    fun dismissQuizSummary() {
        _uiState.value = _uiState.value.copy(quizSummary = null)
    }
}