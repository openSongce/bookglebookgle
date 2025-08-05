package com.ssafy.bookglebookgle.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bookglebookgleserver.pdf.grpc.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit

private const val TAG = "PDFSync"

object PdfSyncClientManager {
    private var channel: ManagedChannel? = null
    private var stub: PdfSyncServiceGrpc.PdfSyncServiceStub? = null

    private var groupId: Long? = null
    private var userId: String? = null
    private var onReceiveCallback: ((SyncMessage) -> Unit)? = null

    // 연결 상태 관리
    private var isConnecting = false
    private var isConnected = false
    private var shouldReconnect = true
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    fun connect(groupId: Long, userId: String, onReceive: (SyncMessage) -> Unit) {
        Log.d(TAG, "연결 요청: groupId=$groupId, userId=$userId")

        // 이미 같은 설정으로 연결 중이거나 연결된 경우
        if ((isConnecting || isConnected) && this.groupId == groupId && this.userId == userId) {
            Log.d(TAG, "이미 연결됨/연결 중 - 요청 무시")
            return
        }

        // 기존 연결 정리
        disconnect()

        this.groupId = groupId
        this.userId = userId
        this.onReceiveCallback = onReceive
        this.shouldReconnect = true
        this.reconnectAttempts = 0

        performConnect()
    }

    private fun performConnect() {
        val gid = groupId ?: return
        val uid = userId ?: return
        val callback = onReceiveCallback ?: return

        if (isConnecting || !shouldReconnect) {
            Log.d(TAG, "연결 시도 스킵: isConnecting=$isConnecting, shouldReconnect=$shouldReconnect")
            return
        }

        isConnecting = true
        Log.d(TAG, "gRPC 연결 시작: groupId=$gid, userId=$uid, 시도=${reconnectAttempts + 1}")

        try {
            channel = ManagedChannelBuilder
                .forAddress("52.79.59.66", 6565)
                .usePlaintext()
                .keepAliveTime(20, TimeUnit.SECONDS)      // 20초마다 keepalive
                .keepAliveTimeout(5, TimeUnit.SECONDS)    // keepalive 응답 대기 시간
                .keepAliveWithoutCalls(true)              // 요청이 없어도 keepalive 전송
                .maxInboundMessageSize(4 * 1024 * 1024)   // 4MB
                .idleTimeout(60, TimeUnit.SECONDS)        // 60초 유휴 타임아웃
                .build()

            stub = PdfSyncServiceGrpc.newStub(channel)

            val request = JoinRequest.newBuilder()
                .setGroupId(gid)
                .setUserId(uid)
                .build()

            stub?.joinRoom(request, object : StreamObserver<SyncMessage> {
                override fun onNext(value: SyncMessage) {
                    // 첫 메시지 수신 시 연결 성공으로 간주
                    if (!isConnected) {
                        isConnected = true
                        isConnecting = false
                        reconnectAttempts = 0
                        Log.d(TAG, "gRPC 연결 성공 확인")
                    }

                    Log.d(TAG, "메시지 수신: page=${value.currentPage}, from=${value.userId}")

                    // 메인 스레드에서 콜백 실행
                    Handler(Looper.getMainLooper()).post {
                        callback(value)
                    }
                }

                override fun onError(t: Throwable) {
                    Log.e(TAG, "gRPC 스트림 오류: ${t.message}")
                    Log.e(TAG, "오류 상세: ${t.javaClass.simpleName}")

                    isConnected = false
                    isConnecting = false

                    if (shouldReconnect) {
                        scheduleReconnect()
                    }
                }

                override fun onCompleted() {
                    Log.d(TAG, "서버가 스트림 완료")
                    isConnected = false
                    isConnecting = false

                    if (shouldReconnect) {
                        scheduleReconnect()
                    }
                }
            })

            // 연결 시작 후 타임아웃 설정 (10초 후에도 연결되지 않으면 재시도)
            reconnectHandler.postDelayed({
                if (isConnecting && !isConnected) {
                    Log.w(TAG, "연결 타임아웃 - 재시도")
                    isConnecting = false
                    scheduleReconnect()
                }
            }, 10000)

        } catch (e: Exception) {
            Log.e(TAG, "gRPC 연결 생성 중 예외", e)
            isConnecting = false
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) {
            Log.d(TAG, "재연결 비활성화됨 - 재연결 중단")
            return
        }

        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "최대 재연결 시도 횟수($maxReconnectAttempts) 초과 - 재연결 중단")
            shouldReconnect = false
            return
        }

        // 기존 재연결 스케줄 취소
        reconnectRunnable?.let { reconnectHandler.removeCallbacks(it) }

        reconnectAttempts++

        // 지수 백오프: 1초, 2초, 4초, 8초... 최대 30초
        val delay = (1000L * Math.pow(2.0, (reconnectAttempts - 1).toDouble()))
            .toLong()
            .coerceAtMost(30000L)

        Log.d(TAG, "재연결 예약: ${delay}ms 후, 시도 횟수: $reconnectAttempts")

        reconnectRunnable = Runnable {
            if (shouldReconnect) {
                Log.d(TAG, "재연결 시도 실행...")
                performConnect()
            }
        }

        reconnectHandler.postDelayed(reconnectRunnable!!, delay)
    }

    fun sendPageUpdate(page: Int) {
        val gid = groupId
        val uid = userId
        val currentStub = stub
        val currentChannel = channel

        if (currentStub == null || gid == null || uid == null) {
            Log.w(TAG, "전송 불가 - 연결 정보 없음")
            return
        }

        if (currentChannel?.isShutdown == true || currentChannel?.isTerminated == true) {
            Log.w(TAG, "채널이 종료됨 - sendPageUpdate 중단")
            return
        }

        if (!isConnected) {
            Log.w(TAG, "연결되지 않음 - sendPageUpdate 중단")
            return
        }

        val message = SyncMessage.newBuilder()
            .setGroupId(gid)
            .setUserId(uid)
            .setCurrentPage(page)
            .build()

        Log.d(TAG, "페이지 전송: $page (userId=$uid)")

        try {
            currentStub.sendMessage(message, object : StreamObserver<Ack> {
                override fun onNext(value: Ack) {
                    Log.d(TAG, "ACK 수신: ${value.message}")
                }

                override fun onError(t: Throwable) {
                    Log.e(TAG, "페이지 전송 오류: ${t.message}")
                }

                override fun onCompleted() {
                    Log.d(TAG, "페이지 전송 완료")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "페이지 전송 예외", e)
        }
    }

    fun disconnect() {
        Log.d(TAG, "연결 종료 시작")
        shouldReconnect = false
        isConnected = false
        isConnecting = false

        // 재연결 스케줄 취소
        reconnectRunnable?.let {
            reconnectHandler.removeCallbacks(it)
            reconnectRunnable = null
        }

        try {
            channel?.let { ch ->
                if (!ch.isShutdown) {
                    ch.shutdown()
                    try {
                        if (!ch.awaitTermination(1, TimeUnit.SECONDS)) {
                            ch.shutdownNow()
                        }
                    } catch (e: InterruptedException) {
                        ch.shutdownNow()
                        Thread.currentThread().interrupt()
                    }
                }
            }
            Log.d(TAG, "gRPC 연결 정상 종료")
        } catch (e: Exception) {
            Log.e(TAG, "연결 종료 중 오류", e)
        } finally {
            channel = null
            stub = null
            groupId = null
            userId = null
            onReceiveCallback = null
        }
    }

    fun getConnectionStatus(): String {
        return "Connected: $isConnected, Connecting: $isConnecting, Attempts: $reconnectAttempts"
    }
}