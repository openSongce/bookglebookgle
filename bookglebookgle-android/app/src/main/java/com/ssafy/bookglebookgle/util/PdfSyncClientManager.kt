package com.ssafy.bookglebookgle.util

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

    fun connect(groupId: Long, userId: String, onReceive: (SyncMessage) -> Unit) {
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
                }

                override fun onCompleted() {
                    Log.d(TAG, "🟢 서버로부터 완료 신호 수신")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ gRPC 연결 예외 발생", e)
        }
    }

    fun sendPageUpdate(userId: String, page: Int) {
        val message = SyncMessage.newBuilder()
            .setUserId(userId)
            .setCurrentPage(page)
            .build()

        stub?.sendMessage(message, object : StreamObserver<Ack> {
            override fun onNext(value: Ack) {
                Log.d(TAG, "Ack: ${value.message}")
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "Send error", t)
            }

            override fun onCompleted() {
                Log.d(TAG, "Send 완료")
            }
        }) ?: Log.w(TAG, "⚠️ stub이 null입니다. 연결되지 않았을 수 있음")
    }

    fun disconnect() {
        channel?.shutdownNow()
        channel = null
        stub = null
        Log.d(TAG, "📴 gRPC 연결 종료")
    }
}

