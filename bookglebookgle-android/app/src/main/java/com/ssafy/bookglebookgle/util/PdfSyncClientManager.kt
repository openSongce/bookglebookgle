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

    fun connect(groupId: Long, userId: String, onReceive: (SyncMessage) -> Unit) {
        if (channel?.isShutdown == false && channel?.isTerminated == false) {
            Log.d(TAG, "이미 gRPC 연결이 활성화됨 - 재연결 생략")
            return
        }

        this.groupId = groupId
        this.userId = userId
        this.onReceiveCallback = onReceive

        try {
            channel = ManagedChannelBuilder
                .forAddress("52.79.59.66", 6565)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS) // 연결 유지
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build()

            stub = PdfSyncServiceGrpc.newStub(channel)

            val request = JoinRequest.newBuilder()
                .setGroupId(groupId)
                .setUserId(userId)
                .build()

            stub?.joinRoom(request, object : StreamObserver<SyncMessage> {
                override fun onNext(value: SyncMessage) {
                    Log.d(TAG, "페이지 동기화 메시지 수신: page=${value.currentPage}, from=${value.userId}")

                    // 메인 스레드에서 콜백 실행
                    Handler(Looper.getMainLooper()).post {
                        onReceive(value)
                    }
                }

                override fun onError(t: Throwable) {
                    Log.e(TAG, "gRPC 오류 발생", t)
                    Handler(Looper.getMainLooper()).postDelayed({
                        reconnect()
                    }, 2000) // 2초 후 재연결
                }

                override fun onCompleted() {
                    Log.d(TAG, "서버로부터 완료 신호 수신")
                    Handler(Looper.getMainLooper()).postDelayed({
                        reconnect()
                    }, 1000) // 1초 후 재연결
                }
            })

            Log.d(TAG, "gRPC 연결 완료: groupId=$groupId, userId=$userId")
        } catch (e: Exception) {
            Log.e(TAG, "gRPC 연결 예외 발생", e)
        }
    }

    fun sendPageUpdate(page: Int) {
        val gid = groupId
        val uid = userId
        val currentStub = stub

        if (currentStub == null || gid == null || uid == null) {
            Log.w(TAG, "전송 불가 - 연결되지 않았거나 정보 없음")
            return
        }

        if (channel?.isShutdown == true || channel?.isTerminated == true) {
            Log.w(TAG, "채널이 종료됨 - sendPageUpdate 중단")
            return
        }

        val message = SyncMessage.newBuilder()
            .setGroupId(gid)
            .setUserId(uid)
            .setCurrentPage(page)
            .build()

        Log.d(TAG, "페이지 전송 시도: $page (userId=$uid)")

        currentStub.sendMessage(message, object : StreamObserver<Ack> {
            override fun onNext(value: Ack) {
                Log.d(TAG, "ACK 수신: ${value.message}")
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "전송 중 오류 발생", t)
            }

            override fun onCompleted() {
                Log.d(TAG, "전송 완료 콜백")
            }
        })
    }

    fun disconnect() {
        try {
            channel?.shutdownNow()
            Log.d(TAG, "gRPC 연결 종료됨")
        } catch (e: Exception) {
            Log.e(TAG, "연결 종료 중 오류", e)
        } finally {
            channel = null
            stub = null
        }
    }

    private fun reconnect() {
        disconnect()

        val gid = groupId
        val uid = userId
        val callback = onReceiveCallback

        if (gid != null && uid != null && callback != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "재연결 시도 중...")
                connect(gid, uid, callback)
            }, 1000)
        } else {
            Log.w(TAG, "재연결 실패: 저장된 정보 부족 (groupId/userId/onReceive)")
        }
    }
}
