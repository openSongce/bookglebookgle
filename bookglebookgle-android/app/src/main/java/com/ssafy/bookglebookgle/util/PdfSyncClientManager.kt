package com.ssafy.bookglebookgle.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bookglebookgleserver.pdf.grpc.Ack
import com.example.bookglebookgleserver.pdf.grpc.JoinRequest
import com.example.bookglebookgleserver.pdf.grpc.PdfSyncServiceGrpc
import com.example.bookglebookgleserver.pdf.grpc.SyncMessage
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver

private const val TAG = "PDFSync"

object PdfSyncClientManager {
    private var channel: ManagedChannel? = null
    private var stub: PdfSyncServiceGrpc.PdfSyncServiceStub? = null
    private var groupId: Long? = null
    private var userId: String? = null
    private var onReceiveCallback: ((SyncMessage) -> Unit)? = null

    fun connect(groupId: Long, userId: String, onReceive: (SyncMessage) -> Unit) {
        this.groupId = groupId
        this.userId = userId
        this.onReceiveCallback = onReceive

        try {
            channel = ManagedChannelBuilder
                .forAddress("52.79.59.66", 6565)
                .usePlaintext()
                .build()

            stub = PdfSyncServiceGrpc.newStub(channel)

            val request = JoinRequest.newBuilder()
                .setGroupId(groupId)
                .setUserId(userId)
                .build()

            stub?.joinRoom(request, object : StreamObserver<SyncMessage> {
                override fun onNext(value: SyncMessage) {
                    Log.d(TAG, "✅ 받은 메시지: ${value.currentPage}")
                    onReceive(value)
                }

                override fun onError(t: Throwable) {
                    Log.e(TAG, "❌ gRPC 오류 발생", t)
                    reconnect()
                }

                override fun onCompleted() {
                    Log.d(TAG, "🟢 서버로부터 완료 신호 수신")
                    reconnect()
                }
            })

            Log.d(TAG, "📡 gRPC 연결 완료")

        } catch (e: Exception) {
            Log.e(TAG, "❌ gRPC 연결 예외 발생", e)
        }
    }

    fun sendPageUpdate(userId: String, page: Int) {
        val currentStub = stub
        if (currentStub == null) {
            Log.w(TAG, "⚠️ 메시지를 보낼 수 없습니다. 연결이 끊겼거나 아직 연결되지 않았습니다.")
            return
        }

        val message = SyncMessage.newBuilder()
            .setUserId(userId)
            .setCurrentPage(page)
            .build()

        currentStub.sendMessage(message, object : StreamObserver<Ack> {
            override fun onNext(value: Ack) {
                Log.d(TAG, "Ack: ${value.message}")
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "❌ Send error", t)
            }

            override fun onCompleted() {
                Log.d(TAG, "📤 메시지 전송 완료")
            }
        })
    }

    fun disconnect() {
        channel?.shutdownNow()
        channel = null
        stub = null
        Log.d(TAG, "📴 gRPC 연결 종료")
    }

    private fun reconnect() {
        disconnect()
        val gid = groupId
        val uid = userId
        val callback = onReceiveCallback

        if (gid != null && uid != null && callback != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "🔄 재연결 시도 중...")
                connect(gid, uid, callback)
            }, 1000) // 1초 후 재연결
        } else {
            Log.w(TAG, "⚠️ 재연결 정보 부족 (groupId/userId/onReceive 없음)")
        }
    }
}
