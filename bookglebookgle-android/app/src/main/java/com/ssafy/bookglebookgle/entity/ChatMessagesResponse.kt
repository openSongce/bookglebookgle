package com.ssafy.bookglebookgle.entity


// REST API 채팅 메시지
data class ChatMessagesResponse(
    val id: Long,
    val userId: Long,
    val userNickname: String,
    val message: String,
    val createdAt: String,
)

// gRPC 채팅 메시지
data class ChatMessage(
    val messageId: Long,
    val userId: Long,
    val nickname: String,
    val profileImage: String?,
    val message: String,
    val timestamp: String,
    val type: MessageType = MessageType.NORMAL,
    val aiResponse: String? = null,
    val suggestedTopics: List<String> = emptyList(),
    // 퀴즈 관련 필드 추가
    val quizStart: QuizStart? = null,
    val quizQuestion: QuizQuestion? = null,
    val quizAnswer: QuizAnswerSubmit? = null,
    val quizReveal: QuizReveal? = null,
    val quizSummary: QuizSummary? = null,
    val quizEnd: QuizEnd? = null,

    val avatarBgColor: String? = null
)

enum class MessageType(val value: String) {
    NORMAL("NORMAL"),
    AI_RESPONSE("AI_RESPONSE"),
    DISCUSSION_START("DISCUSSION_START"),
    DISCUSSION_END("DISCUSSION_END"),
    // 퀴즈 관련 타입 추가
    QUIZ_START("QUIZ_START"),
    QUIZ_QUESTION("QUIZ_QUESTION"),
    QUIZ_ANSWER("QUIZ_ANSWER"),
    QUIZ_REVEAL("QUIZ_REVEAL"),
    QUIZ_SUMMARY("QUIZ_SUMMARY"),
    QUIZ_END("QUIZ_END");

    companion object {
        fun fromString(value: String): MessageType {
            return entries.find { it.value == value } ?: NORMAL
        }
    }
}

// 퀴즈 단계 enum
enum class QuizPhase(val value: Int) {
    QUIZ_PHASE_UNKNOWN(0),
    MIDTERM(1),
    FINAL(2);

    companion object {
        fun fromValue(value: Int): QuizPhase {
            return entries.find { it.value == value } ?: QUIZ_PHASE_UNKNOWN
        }
    }
}

// 퀴즈 시작 데이터
data class QuizStart(
    val groupId: Long,
    val meetingId: String,
    val documentId: String,
    val phase: QuizPhase,
    val progressPercentage: Int, // 50 or 100
    val totalQuestions: Int, // 보통 4
    val quizId: String,
    val startedAt: Long
)

// 퀴즈 문제 데이터
data class QuizQuestion(
    val quizId: String,
    val questionIndex: Int,
    val questionText: String,
    val options: List<String>,
    val timeoutSeconds: Int, // 15
    val issuedAt: Long,
    val correctAnswerIndex: Int = -1 // 클라이언트에서는 보통 -1로 초기화
)

// 퀴즈 답안 제출 데이터
data class QuizAnswerSubmit(
    val quizId: String,
    val questionIndex: Int,
    val selectedIndex: Int // 0~3
)

// 퀴즈 정답 공개 데이터
data class QuizReveal(
    val quizId: String,
    val questionIndex: Int,
    val correctAnswerIndex: Int,
    val userAnswers: List<PerUserAnswer>
)

// 사용자별 답안 데이터
data class PerUserAnswer(
    val userId: Long,
    val selectedIndex: Int,
    val isCorrect: Boolean
)

// 퀴즈 요약 데이터
data class QuizSummary(
    val quizId: String,
    val phase: QuizPhase,
    val totalQuestions: Int,
    val scores: List<UserScore>
)

// 사용자 점수 데이터
data class UserScore(
    val userId: Long,
    val nickname: String,
    val correctCount: Int,
    val rank: Int
)

// 퀴즈 종료 데이터
data class QuizEnd(
    val quizId: String,
    val reason: String // COMPLETED, CANCELLED, ERROR
)