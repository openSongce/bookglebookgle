package com.ssafy.bookglebookgle.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.bookglebookgleserver.chat.ChatMessage as GrpcChatMessage
import com.example.bookglebookgleserver.chat.ChatServiceGrpc
import com.example.bookglebookgleserver.chat.PerUserAnswer
import com.example.bookglebookgleserver.chat.QuizAnswerSubmit
import com.example.bookglebookgleserver.chat.QuizEnd
import com.example.bookglebookgleserver.chat.QuizPhase
import com.example.bookglebookgleserver.chat.QuizQuestion
import com.example.bookglebookgleserver.chat.QuizReveal
import com.example.bookglebookgleserver.chat.QuizStart
import com.example.bookglebookgleserver.chat.QuizSummary
import com.example.bookglebookgleserver.chat.UserScore
import com.kakao.sdk.common.KakaoSdk.type
import com.ssafy.bookglebookgle.entity.ChatMessage
import com.ssafy.bookglebookgle.entity.MessageType
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "싸피_ChatGrpcRepository"

sealed class GrpcConnectionStatus {
    object DISCONNECTED : GrpcConnectionStatus()
    object CONNECTING : GrpcConnectionStatus()
    object CONNECTED : GrpcConnectionStatus()
    data class ERROR(val message: String) : GrpcConnectionStatus()
}

@Singleton
class ChatGrpcRepository @Inject constructor(
    private val channel: ManagedChannel,  // 의존성 주입
    private val chatStub: ChatServiceGrpc.ChatServiceStub  // 의존성 주입
) {

    private var requestObserver: StreamObserver<GrpcChatMessage>? = null

    // 실시간 메시지 스트림
    private val _newMessages = MutableLiveData<ChatMessage>()
    val newMessages: LiveData<ChatMessage> = _newMessages

    // AI 응답 전용 스트림
    private val _aiResponses = MutableLiveData<ChatMessage>()
    val aiResponses: LiveData<ChatMessage> = _aiResponses

    // 토론 상태 변화 스트림
    private val _discussionStatus = MutableLiveData<ChatMessage>()
    val discussionStatus: LiveData<ChatMessage> = _discussionStatus

    // 퀴즈 메시지 전용 스트림 - 새로 추가
    private val _quizMessages = MutableLiveData<ChatMessage>()
    val quizMessages: LiveData<ChatMessage> = _quizMessages

    // 연결 상태
    private val _connectionStatus = MutableLiveData<GrpcConnectionStatus>()
    val connectionStatus: LiveData<GrpcConnectionStatus> = _connectionStatus

    // 현재 채팅방 정보
    private var currentGroupId: Long = 0
    private var currentUserId: Long = 0
    private var currentUserName: String = ""

    init {
        // 의존성 주입으로 받은 스텁이므로 바로 연결됨
        _connectionStatus.value = GrpcConnectionStatus.CONNECTED
        Log.d(TAG, "gRPC Repository 초기화 완료")
    }

    fun connect() {
        Log.d(TAG, "connect() 호출됨 - 이미 DI로 채널/스텁 초기화됨")
        _connectionStatus.value = GrpcConnectionStatus.CONNECTED
    }

    fun joinChatRoom(groupId: Long, userId: Long, userName: String) {
        Log.d(TAG, "joinChatRoom 호출됨")
        Log.d(TAG, "전달받은 값: groupId=$groupId, userId=$userId, userName=$userName")
        currentGroupId = groupId
        currentUserId = userId
        currentUserName = userName

        Log.d(TAG, "채팅방 입장 시작: groupId=$groupId, userId=$userId, userName=$userName")

        // 메시지를 받을 Observer
        val responseObserver = object : StreamObserver<GrpcChatMessage> {
            override fun onNext(grpcMessage: GrpcChatMessage) {
                // 같은 채팅방 메시지만 처리
                if (grpcMessage.groupId == currentGroupId) {
                    // gRPC 메시지를 Kotlin 데이터 클래스로 변환
                    val chatMessage = grpcToChatMessage(grpcMessage)

                    Handler(Looper.getMainLooper()).post {
                        Log.d(TAG, "실시간 메시지 수신: ${chatMessage.nickname}: ${chatMessage.message}")

                        // 시스템 입장/퇴장 메시지 필터링 - 채팅방에 표시하지 않음
                        if (isSystemJoinLeaveMessage(chatMessage)) {
                            Log.d(TAG, "시스템 입장/퇴장 메시지 필터링됨: ${chatMessage.message}")
                            return@post // 이 메시지는 UI에 표시하지 않음
                        }

                        // 메시지 타입별로 다른 LiveData로 분배
                        when (chatMessage.type) {
                            MessageType.AI_RESPONSE -> {
                                _aiResponses.value = chatMessage
                            }
                            MessageType.DISCUSSION_START, MessageType.DISCUSSION_END -> {
                                _discussionStatus.value = chatMessage
                            }
                            // 퀴즈 관련 메시지 타입들 추가
                            MessageType.QUIZ_START, MessageType.QUIZ_QUESTION, MessageType.QUIZ_ANSWER,
                            MessageType.QUIZ_REVEAL, MessageType.QUIZ_SUMMARY, MessageType.QUIZ_END -> {
                                _quizMessages.value = chatMessage
                            }
                            else -> {
                                _newMessages.value = chatMessage
                            }
                        }
                    }
                }
            }

            override fun onError(t: Throwable) {
                Handler(Looper.getMainLooper()).post {
                    _connectionStatus.value = GrpcConnectionStatus.ERROR(t.message ?: "스트림 오류")
                    Log.e(TAG, "gRPC 스트림 오류", t)

                    // 3초 후 재연결 시도
                    Handler(Looper.getMainLooper()).postDelayed({
                        reconnect()
                    }, 3000)
                }
            }

            override fun onCompleted() {
                Handler(Looper.getMainLooper()).post {
                    _connectionStatus.value = GrpcConnectionStatus.DISCONNECTED
                    Log.d(TAG, "gRPC 스트림 완료")
                }
            }
        }

        // 양방향 스트리밍 시작
        requestObserver = chatStub.chat(responseObserver)  // 의존성 주입받은 스텁 사용

        sendSystemMessage("$currentUserName 님이 채팅방에 입장했습니다.")

        Log.d(TAG, "채팅방 입장 완료")
    }

    // 시스템 입장/퇴장 메시지인지 확인하는 함수
    private fun isSystemJoinLeaveMessage(chatMessage: ChatMessage): Boolean {
        // 시스템 메시지이고 입장/퇴장 키워드가 포함된 경우
        return chatMessage.nickname == "시스템" &&
                chatMessage.type == MessageType.NORMAL &&
                (chatMessage.message.contains("입장했습니다") ||
                        chatMessage.message.contains("퇴장했습니다"))
    }

    // 일반 메시지 전송
    fun sendMessage(content: String) {
        sendTypedMessage(content, MessageType.NORMAL)
    }

    // 토론 시작 신호 전송
    fun startDiscussion(content: String = "토론을 시작합니다.") {
        sendTypedMessage(content, MessageType.DISCUSSION_START)
    }

    // 토론 종료 신호 전송
    fun endDiscussion(content: String = "토론을 종료합니다.") {
        sendTypedMessage(content, MessageType.DISCUSSION_END)
    }

    // 퀴즈 시작 - 새로 추가
    fun startQuiz(
        groupId: Long,
        meetingId: String,
        documentId: String,
        phase: com.ssafy.bookglebookgle.entity.QuizPhase,
        progressPercentage: Int
    ) {
        val quizStart = QuizStart.newBuilder()
            .setGroupId(groupId)
            .setMeetingId(meetingId)
            .setDocumentId(documentId)
            .setPhase(QuizPhase.forNumber(phase.value))
            .setProgressPercentage(progressPercentage)
            .setTotalQuestions(4) // 기본값
            .setStartedAt(System.currentTimeMillis())
            .build()

        val grpcMessage = GrpcChatMessage.newBuilder()
            .setGroupId(currentGroupId)
            .setSenderId(currentUserId)
            .setSenderName(currentUserName)
            .setContent("퀴즈를 시작합니다!")
            .setTimestamp(System.currentTimeMillis())
            .setType("QUIZ_START")
            .setQuizStart(quizStart)
            .build()

        try {
            requestObserver?.onNext(grpcMessage)
            Log.d(TAG, "퀴즈 시작 메시지 전송 성공")
        } catch (e: Exception) {
            Log.e(TAG, "퀴즈 시작 메시지 전송 실패", e)
        }
    }

    // 퀴즈 답안 제출 - 새로 추가
    fun submitQuizAnswer(quizId: String, questionIndex: Int, selectedIndex: Int) {
        val quizAnswer = QuizAnswerSubmit.newBuilder()
            .setQuizId(quizId)
            .setQuestionIndex(questionIndex)
            .setSelectedIndex(selectedIndex)
            .build()

        val grpcMessage = GrpcChatMessage.newBuilder()
            .setGroupId(currentGroupId)
            .setSenderId(currentUserId)
            .setSenderName(currentUserName)
            .setContent("답안을 제출했습니다.")
            .setTimestamp(System.currentTimeMillis())
            .setType("QUIZ_ANSWER")
            .setQuizAnswer(quizAnswer)
            .build()

        try {
            requestObserver?.onNext(grpcMessage)
            Log.d(TAG, "퀴즈 답안 제출 성공: $selectedIndex")
        } catch (e: Exception) {
            Log.e(TAG, "퀴즈 답안 제출 실패", e)
            _connectionStatus.value = GrpcConnectionStatus.ERROR("답안 제출 실패")
        }
    }

    // 퀴즈 종료 - 새로 추가
    fun endQuiz(reason: String) {
        val quizEnd = QuizEnd.newBuilder()
            .setQuizId("") // 서버에서 현재 퀴즈 ID 사용
            .setReason(reason)
            .build()

        val grpcMessage = GrpcChatMessage.newBuilder()
            .setGroupId(currentGroupId)
            .setSenderId(currentUserId)
            .setSenderName(currentUserName)
            .setContent(reason)
            .setTimestamp(System.currentTimeMillis())
            .setType("QUIZ_END")
            .setQuizEnd(quizEnd)
            .build()

        try {
            requestObserver?.onNext(grpcMessage)
            Log.d(TAG, "퀴즈 종료 메시지 전송 성공")
        } catch (e: Exception) {
            Log.e(TAG, "퀴즈 종료 메시지 전송 실패", e)
            _connectionStatus.value = GrpcConnectionStatus.ERROR("퀴즈 종료 실패")
        }
    }

    // 타입별 메시지 전송 (내부 함수)
    private fun sendTypedMessage(content: String, type: MessageType) {
        if (requestObserver == null) {
            Log.e(TAG, "채팅방에 참여하지 않았습니다.")
            return
        }

        if (content.isBlank() && type == MessageType.NORMAL) {
            Log.w(TAG, "빈 메시지는 전송할 수 없습니다.")
            return
        }

        val grpcMessage = GrpcChatMessage.newBuilder()
            .setGroupId(currentGroupId)
            .setSenderId(currentUserId)
            .setSenderName(currentUserName)
            .setContent(content)
            .setTimestamp(System.currentTimeMillis())
            .setType(type.value)
            .build()

        try {
            requestObserver?.onNext(grpcMessage)
            Log.d(TAG, "메시지 전송: groupId=$currentGroupId, senderId=$currentUserId, senderName=$currentUserName, type=${type.value}")

        } catch (e: Exception) {
            Log.e(TAG, "메시지 전송 실패", e)
            _connectionStatus.value = GrpcConnectionStatus.ERROR("메시지 전송 실패")
        }
    }

    private fun sendSystemMessage(content: String) {
        if (requestObserver == null) return

        val systemMessage = GrpcChatMessage.newBuilder()
            .setGroupId(currentGroupId)
            .setSenderId(0)
            .setSenderName("시스템")
            .setContent(content)
            .setTimestamp(System.currentTimeMillis())
            .setType("NORMAL")
            .build()

        try {
            requestObserver?.onNext(systemMessage)
            Log.d(TAG, "시스템 메시지 전송: [${type.value}] $content")
        } catch (e: Exception) {
            Log.e(TAG, "시스템 메시지 전송 실패", e)
        }
    }

    fun leaveChatRoom() {
        if (currentUserName.isNotEmpty()) {
            sendSystemMessage("$currentUserName 님이 퇴장했습니다.")
        }

        try {
            requestObserver?.onCompleted()
        } catch (e: Exception) {
            Log.e(TAG, "스트림 종료 중 오류", e)
        } finally {
            requestObserver = null
            Log.d(TAG, "채팅방 퇴장 완료")
        }
    }

    fun disconnect() {
        leaveChatRoom()

        try {
            Log.d(TAG, "gRPC 채널 종료")
        } catch (e: Exception) {
            Log.e(TAG, "채널 종료 중 오류", e)
        } finally {
            _connectionStatus.value = GrpcConnectionStatus.DISCONNECTED
        }
    }

    private fun reconnect() {
        if (currentGroupId != 0L && currentUserName.isNotEmpty()) {
            Log.d(TAG, "gRPC 재연결 시도...")

            val groupId = currentGroupId
            val userId = currentUserId
            val userName = currentUserName

            // 기존 스트림만 정리하고 다시 참여
            requestObserver?.onCompleted()
            requestObserver = null

            Handler(Looper.getMainLooper()).postDelayed({
                joinChatRoom(groupId, userId, userName)
            }, 1000)
        }
    }

    // gRPC 메시지를 Kotlin 데이터 클래스로 변환
    private fun grpcToChatMessage(grpcMessage: GrpcChatMessage): ChatMessage {
        val messageType = MessageType.fromString(grpcMessage.type)

        // 퀴즈 메시지의 경우 더 자세한 내용 추가
        val displayMessage = when (messageType) {
            MessageType.QUIZ_START -> "퀴즈가 시작되었습니다!"
            MessageType.QUIZ_QUESTION -> "새로운 문제가 출제되었습니다"
            MessageType.QUIZ_ANSWER -> ""
            MessageType.QUIZ_REVEAL -> "정답이 공개되었습니다"
            MessageType.QUIZ_SUMMARY -> "퀴즈 결과가 나왔습니다"
            MessageType.QUIZ_END -> "퀴즈가 종료되었습니다"
            else -> grpcMessage.content
        }
        return ChatMessage(
            messageId = grpcMessage.timestamp,
            userId = grpcMessage.senderId,
            nickname = grpcMessage.senderName,
            profileImage = grpcMessage.avatarKey.takeIf { it.isNotBlank() },
            message = displayMessage,
            timestamp = formatTimestamp(grpcMessage.timestamp),
            type = messageType,
            aiResponse = if (grpcMessage.aiResponse.isNotEmpty()) grpcMessage.aiResponse else null,
            suggestedTopics = grpcMessage.suggestedTopicsList,
            // 퀴즈 관련 필드들 변환
            quizStart = if (grpcMessage.hasQuizStart()) convertQuizStart(grpcMessage.quizStart) else null,
            quizQuestion = if (grpcMessage.hasQuizQuestion()) convertQuizQuestion(grpcMessage.quizQuestion) else null,
            quizAnswer = if (grpcMessage.hasQuizAnswer()) convertQuizAnswer(grpcMessage.quizAnswer) else null,
            quizReveal = if (grpcMessage.hasQuizReveal()) convertQuizReveal(grpcMessage.quizReveal) else null,
            quizSummary = if (grpcMessage.hasQuizSummary()) convertQuizSummary(grpcMessage.quizSummary) else null,
            quizEnd = if (grpcMessage.hasQuizEnd()) convertQuizEnd(grpcMessage.quizEnd) else null,
            avatarBgColor = grpcMessage.avatarBgColor.takeIf { it.isNotBlank() }
        )
    }

    // gRPC QuizStart를 entity QuizStart로 변환
    private fun convertQuizStart(grpcQuizStart: QuizStart): com.ssafy.bookglebookgle.entity.QuizStart {
        return com.ssafy.bookglebookgle.entity.QuizStart(
            groupId = grpcQuizStart.groupId,
            meetingId = grpcQuizStart.meetingId,
            documentId = grpcQuizStart.documentId,
            phase = com.ssafy.bookglebookgle.entity.QuizPhase.fromValue(grpcQuizStart.phaseValue),
            progressPercentage = grpcQuizStart.progressPercentage,
            totalQuestions = grpcQuizStart.totalQuestions,
            quizId = grpcQuizStart.quizId,
            startedAt = grpcQuizStart.startedAt
        )
    }

    // gRPC QuizQuestion을 entity QuizQuestion으로 변환
    private fun convertQuizQuestion(grpcQuizQuestion: QuizQuestion): com.ssafy.bookglebookgle.entity.QuizQuestion {
        return com.ssafy.bookglebookgle.entity.QuizQuestion(
            quizId = grpcQuizQuestion.quizId,
            questionIndex = grpcQuizQuestion.questionIndex,
            questionText = grpcQuizQuestion.questionText,
            options = grpcQuizQuestion.optionsList,
            timeoutSeconds = grpcQuizQuestion.timeoutSeconds,
            issuedAt = grpcQuizQuestion.issuedAt,
            correctAnswerIndex = grpcQuizQuestion.correctAnswerIndex
        )
    }

    // gRPC QuizAnswerSubmit을 entity QuizAnswerSubmit으로 변환
    private fun convertQuizAnswer(grpcQuizAnswer: QuizAnswerSubmit): com.ssafy.bookglebookgle.entity.QuizAnswerSubmit {
        return com.ssafy.bookglebookgle.entity.QuizAnswerSubmit(
            quizId = grpcQuizAnswer.quizId,
            questionIndex = grpcQuizAnswer.questionIndex,
            selectedIndex = grpcQuizAnswer.selectedIndex
        )
    }

    // gRPC QuizReveal을 entity QuizReveal로 변환
    private fun convertQuizReveal(grpcQuizReveal: QuizReveal): com.ssafy.bookglebookgle.entity.QuizReveal {
        return com.ssafy.bookglebookgle.entity.QuizReveal(
            quizId = grpcQuizReveal.quizId,
            questionIndex = grpcQuizReveal.questionIndex,
            correctAnswerIndex = grpcQuizReveal.correctAnswerIndex,
            userAnswers = grpcQuizReveal.userAnswersList.map { convertPerUserAnswer(it) }
        )
    }

    // gRPC PerUserAnswer를 entity PerUserAnswer로 변환
    private fun convertPerUserAnswer(grpcPerUserAnswer: PerUserAnswer): com.ssafy.bookglebookgle.entity.PerUserAnswer {
        return com.ssafy.bookglebookgle.entity.PerUserAnswer(
            userId = grpcPerUserAnswer.userId,
            selectedIndex = grpcPerUserAnswer.selectedIndex,
            isCorrect = grpcPerUserAnswer.isCorrect
        )
    }

    // gRPC QuizSummary를 entity QuizSummary로 변환
    private fun convertQuizSummary(grpcQuizSummary: QuizSummary): com.ssafy.bookglebookgle.entity.QuizSummary {
        return com.ssafy.bookglebookgle.entity.QuizSummary(
            quizId = grpcQuizSummary.quizId,
            phase = com.ssafy.bookglebookgle.entity.QuizPhase.fromValue(grpcQuizSummary.phaseValue),
            totalQuestions = grpcQuizSummary.totalQuestions,
            scores = grpcQuizSummary.scoresList.map { convertUserScore(it) }
        )
    }

    // gRPC UserScore를 entity UserScore로 변환
    private fun convertUserScore(grpcUserScore: UserScore): com.ssafy.bookglebookgle.entity.UserScore {
        return com.ssafy.bookglebookgle.entity.UserScore(
            userId = grpcUserScore.userId,
            nickname = grpcUserScore.nickname,
            correctCount = grpcUserScore.correctCount,
            rank = grpcUserScore.rank
        )
    }

    // gRPC QuizEnd를 entity QuizEnd로 변환
    private fun convertQuizEnd(grpcQuizEnd: QuizEnd): com.ssafy.bookglebookgle.entity.QuizEnd {
        return com.ssafy.bookglebookgle.entity.QuizEnd(
            quizId = grpcQuizEnd.quizId,
            reason = grpcQuizEnd.reason
        )
    }

    // 타임스탬프 포맷팅 - 한국어 시간 형식으로 변경
    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val calendar = Calendar.getInstance().apply { time = date }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val period = if (hour < 12) "오전" else "오후"
        val displayHour = if (hour == 0) 12
        else if (hour > 12) hour - 12
        else hour

        return "$period $displayHour:${String.format("%02d", minute)}"
    }
}