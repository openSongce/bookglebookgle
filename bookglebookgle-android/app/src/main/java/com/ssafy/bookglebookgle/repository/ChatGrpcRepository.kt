package com.ssafy.bookglebookgle.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.bookglebookgleserver.chat.ChatMessage as GrpcChatMessage
import com.example.bookglebookgleserver.chat.ChatServiceGrpc
import com.ssafy.bookglebookgle.entity.ChatMessage
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

    // 연결 상태
    private val _connectionStatus = MutableLiveData<GrpcConnectionStatus>()
    val connectionStatus: LiveData<GrpcConnectionStatus> = _connectionStatus

    // 현재 채팅방 정보
    private var currentGroupId: Long = 0
    private var currentUserId: Int = 0
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

    fun joinChatRoom(groupId: Long, userId: Int, userName: String) {
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
                        _newMessages.value = chatMessage
                    }
                    Log.d(TAG, "실시간 메시지 수신: ${chatMessage.nickname}: ${chatMessage.message}")
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

        // 입장 메시지 자동 전송
        sendSystemMessage("$userName 님이 입장했습니다.")

        Log.d(TAG, "채팅방 입장 완료")
    }

    fun sendMessage(content: String) {
        if (requestObserver == null) {
            Log.e(TAG, "채팅방에 참여하지 않았습니다.")
            return
        }

        if (content.isBlank()) {
            Log.w(TAG, "빈 메시지는 전송할 수 없습니다.")
        }

        val grpcMessage = GrpcChatMessage.newBuilder()
            .setGroupId(currentGroupId)
            .setSenderId(currentUserId.toLong())
            .setSenderName(currentUserName)
            .setContent(content)
            .setTimestamp(System.currentTimeMillis())
            .build()

        try {
            requestObserver?.onNext(grpcMessage)
            Log.d(TAG, "메시지 전송 성공: $content")
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
            .build()

        try {
            requestObserver?.onNext(systemMessage)
            Log.d(TAG, "시스템 메시지 전송: $content")
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
        return ChatMessage(
            messageId = grpcMessage.timestamp,
            userId = grpcMessage.senderId.toInt(),
            nickname = grpcMessage.senderName,
            profileImage = null,
            message = grpcMessage.content,
            timestamp = formatTimestamp(grpcMessage.timestamp)
        )
    }

    // 타임스탬프 포맷팅
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "방금 전"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"
            else -> {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}