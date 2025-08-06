package com.ssafy.bookglebookgle.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.bookglebookgleserver.pdf.grpc.AnnotationPayload
import com.example.bookglebookgleserver.pdf.grpc.AnnotationType
import com.example.bookglebookgleserver.pdf.grpc.ActionType
import com.example.bookglebookgleserver.pdf.grpc.PdfSyncServiceGrpc
import com.example.bookglebookgleserver.pdf.grpc.SyncMessage
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PdfSyncRepo"

/**
 * 페이지만 동기화하는 간단 모델
 */
data class PdfPageSync(
    val page: Int,
    val fromUserId: String
)

/**
 * 연결 상태 표현
 */
sealed class PdfSyncConnectionStatus {
    object DISCONNECTED : PdfSyncConnectionStatus()
    object CONNECTING : PdfSyncConnectionStatus()
    object CONNECTED : PdfSyncConnectionStatus()
    data class ERROR(val message: String) : PdfSyncConnectionStatus()
}

/**
 * gRPC 양방향 스트림으로 페이지 이동만 Sync
 */
@Singleton
class PdfGrpcRepositoryImpl @Inject constructor() : PdfGrpcRepository {

    private val _newPageUpdates = MutableLiveData<PdfPageSync>()
    override val newPageUpdates: LiveData<PdfPageSync> = _newPageUpdates

    private val _connectionStatus =
        MutableLiveData<PdfSyncConnectionStatus>(PdfSyncConnectionStatus.DISCONNECTED)
    override val connectionStatus: LiveData<PdfSyncConnectionStatus> = _connectionStatus

    private var channel: ManagedChannel? = null
    private var stub: PdfSyncServiceGrpc.PdfSyncServiceStub? = null
    private var requestObserver: StreamObserver<SyncMessage>? = null

    private var groupId: Long? = null
    private var userId: String? = null

    private var isActive = false
    private var shouldReconnect = false

    override fun connectAndJoinRoom(groupId: Long, userId: String) {
        // 이미 활성 스트림 있으면 skip
        if (isActive && channel?.isTerminated == false) return

        this.groupId = groupId
        this.userId = userId
        isActive = true
        shouldReconnect = true
        _connectionStatus.value = PdfSyncConnectionStatus.CONNECTING

        try {
            channel = ManagedChannelBuilder
                .forAddress("52.79.59.66", 6565)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build()

            stub = PdfSyncServiceGrpc.newStub(channel)

            // 양방향 스트림 열기
            requestObserver = stub!!.sync(object : StreamObserver<SyncMessage> {
                override fun onNext(msg: SyncMessage) {
                    if (!isActive) return
                    if (msg.actionType == ActionType.PAGE_MOVE) {
                        Handler(Looper.getMainLooper()).post {
                            _newPageUpdates.postValue(
                                PdfPageSync(
                                    msg.payload.page,
                                    msg.userId
                                )
                            )
                        }
                    }
                }

                override fun onError(t: Throwable) {
                    if (!shouldReconnect) return
                    isActive = false
                    _connectionStatus.postValue(
                        PdfSyncConnectionStatus.ERROR("연결 오류: ${t.message}")
                    )
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (shouldReconnect) reconnect()
                    }, 2_000)
                }

                override fun onCompleted() {
                    isActive = false
                    _connectionStatus.postValue(PdfSyncConnectionStatus.DISCONNECTED)
                    if (shouldReconnect) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            reconnect()
                        }, 1_000)
                    }
                }
            })

            // 스트림이 열렸으니 연결 성공
            _connectionStatus.value = PdfSyncConnectionStatus.CONNECTED

        } catch (e: Exception) {
            Log.e(TAG, "gRPC 연결 예외 발생", e)
            _connectionStatus.value =
                PdfSyncConnectionStatus.ERROR("연결 실패: ${e.message}")
        }
    }

    override fun sendPageUpdate(page: Int) {
        val obs = requestObserver ?: run {
            Log.w(TAG, "stream not open")
            return
        }
        if (!isActive) return

        val msg = SyncMessage.newBuilder()
            .setGroupId(groupId ?: return)
            .setUserId(userId ?: return)
            .setActionType(ActionType.PAGE_MOVE)
            .setAnnotationType(AnnotationType.PAGE)
            .setPayload(AnnotationPayload.newBuilder().setPage(page).build())
            .build()

        obs.onNext(msg)
    }

    override fun reconnect() {
        if (!shouldReconnect) return
        disconnect()
        val gid = groupId
        val uid = userId
        if (gid != null && uid != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                connectAndJoinRoom(gid, uid)
            }, 1_000)
        } else {
            _connectionStatus.value =
                PdfSyncConnectionStatus.ERROR("재연결 정보 부족")
        }
    }

    override fun leaveRoom() {
        shouldReconnect = false
        isActive = false
        requestObserver?.onCompleted()
        disconnect()
        _connectionStatus.value = PdfSyncConnectionStatus.DISCONNECTED
    }

    override fun isConnected(): Boolean {
        return _connectionStatus.value == PdfSyncConnectionStatus.CONNECTED && isActive
    }

    private fun disconnect() {
        try {
            channel?.shutdownNow()
            Log.d(TAG, "gRPC 연결 종료됨")
        } catch (e: Exception) {
            Log.e(TAG, "연결 종료 중 오류", e)
        } finally {
            channel = null
            stub = null
            requestObserver = null
        }
    }
}
