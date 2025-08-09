package com.ssafy.bookglebookgle.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.bookglebookgleserver.pdf.grpc.AnnotationPayload
import com.example.bookglebookgleserver.pdf.grpc.AnnotationType
import com.example.bookglebookgleserver.pdf.grpc.ActionType
import com.example.bookglebookgleserver.pdf.grpc.Participant as ProtoParticipant
import com.example.bookglebookgleserver.pdf.grpc.ParticipantsSnapshot
import com.example.bookglebookgleserver.pdf.grpc.PdfSyncServiceGrpc
import com.example.bookglebookgleserver.pdf.grpc.SyncMessage
import com.ssafy.bookglebookgle.entity.CommentSync
import com.ssafy.bookglebookgle.entity.HighlightSync
import com.ssafy.bookglebookgle.entity.Participant as GParticipant
import com.ssafy.bookglebookgle.entity.PdfPageSync
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PdfSyncRepo"


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
// gRPC 구현체
@Singleton
class PdfGrpcRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfGrpcRepository {
    private val TAG = "PdfSyncRepo"

    private val _newPageUpdates = MutableLiveData<PdfPageSync>()
    override val newPageUpdates: LiveData<PdfPageSync> = _newPageUpdates

    private val _newHighlights = MutableLiveData<HighlightSync>()
    override val newHighlights: LiveData<HighlightSync> = _newHighlights

    private val _newComments = MutableLiveData<CommentSync>()
    override val newComments: LiveData<CommentSync> = _newComments

    private val _updatedHighlights = MutableLiveData<HighlightSync>()
    override val updatedHighlights: LiveData<HighlightSync> = _updatedHighlights

    private val _updatedComments = MutableLiveData<CommentSync>()
    override val updatedComments: LiveData<CommentSync> = _updatedComments

    private val _deletedHighlights = MutableLiveData<Long>()
    override val deletedHighlights: LiveData<Long> = _deletedHighlights

    private val _deletedComments = MutableLiveData<Long>()
    override val deletedComments: LiveData<Long> = _deletedComments

    private val _connectionStatus = MutableLiveData<PdfSyncConnectionStatus>(PdfSyncConnectionStatus.DISCONNECTED)
    override val connectionStatus: LiveData<PdfSyncConnectionStatus> = _connectionStatus

    private val _leadershipTransfers = MutableLiveData<String>()
    override val leadershipTransfers: LiveData<String> = _leadershipTransfers

    private val _joinRequests = MutableLiveData<String>()
    override val joinRequests: LiveData<String> = _joinRequests

    private val _participantsSnapshot = MutableLiveData<ParticipantsSnapshot>()
    override val participantsSnapshot: LiveData<ParticipantsSnapshot>
        get() = _participantsSnapshot

    private val _progressUpdates = MutableLiveData<Pair<String, Int>>()
    override val progressUpdates: LiveData<Pair<String, Int>> = _progressUpdates


    private val cm: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private var netCb: ConnectivityManager.NetworkCallback? = null

    private var channel: ManagedChannel? = null
    private var stub: PdfSyncServiceGrpc.PdfSyncServiceStub? = null
    private var requestObserver: StreamObserver<SyncMessage>? = null

    private var groupId: Long? = null
    private var userId: String? = null
    private var isActive = false
    private var shouldReconnect = false
    private var backoffMs = 1_000L

    override fun connectAndJoinRoom(groupId: Long, userId: String) {
        if (isActive && channel?.isTerminated == false) return
        this.groupId = groupId
        this.userId = userId
        isActive = true
        shouldReconnect = true
        _connectionStatus.value = PdfSyncConnectionStatus.CONNECTING

        if (netCb == null) {
            netCb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (shouldReconnect) reconnect()
                }
            }
            runCatching { cm.registerDefaultNetworkCallback(netCb!!) }
        }

        try {
            channel = ManagedChannelBuilder
                .forAddress("52.79.59.66", 6565)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build()

            stub = PdfSyncServiceGrpc.newStub(channel)

            requestObserver = stub!!.sync(object : StreamObserver<SyncMessage> {
                override fun onNext(msg: SyncMessage) {
                    if (!isActive) return
                    when (msg.actionType) {
                        ActionType.PROGRESS_UPDATE -> {
                            Handler(Looper.getMainLooper()).post {
                                _progressUpdates.value = msg.userId to msg.payload.page
                            }
                        }
                        ActionType.PARTICIPANTS -> {
                            Handler(Looper.getMainLooper()).post {
                                // ParticipantsSnapshot 메시지 전체 전달
                                _participantsSnapshot.value = msg.participants
                            }
                        }
                        ActionType.JOIN_ROOM -> {
                            Handler(Looper.getMainLooper()).post {
                                _joinRequests.value = msg.userId
                            }
                        }
                        ActionType.PAGE_MOVE -> {
                            Handler(Looper.getMainLooper()).post {
                                _newPageUpdates.value = PdfPageSync(msg.payload.page, msg.userId)
                            }
                        }
                        ActionType.ADD,
                        ActionType.UPDATE -> {
                            when (msg.annotationType) {
                                AnnotationType.HIGHLIGHT -> {
                                    val sync = HighlightSync(
                                        id = msg.payload.id,
                                        page = msg.payload.page,
                                        snippet = msg.payload.snippet,
                                        color = msg.payload.color,
                                        startX = msg.payload.coordinates.startX,
                                        startY = msg.payload.coordinates.startY,
                                        endX = msg.payload.coordinates.endX,
                                        endY = msg.payload.coordinates.endY,
                                        userId = msg.userId
                                    )
                                    Handler(Looper.getMainLooper()).post {
                                        if (msg.actionType == ActionType.ADD) _newHighlights.value = sync
                                        else _updatedHighlights.value = sync
                                    }
                                }
                                AnnotationType.COMMENT -> {
                                    val sync = CommentSync(
                                        id = msg.payload.id,
                                        page = msg.payload.page,
                                        snippet = msg.payload.snippet,
                                        text = msg.payload.text,
                                        startX = msg.payload.coordinates.startX,
                                        startY = msg.payload.coordinates.startY,
                                        endX = msg.payload.coordinates.endX,
                                        endY = msg.payload.coordinates.endY,
                                        userId = msg.userId
                                    )
                                    Handler(Looper.getMainLooper()).post {
                                        if (msg.actionType == ActionType.ADD) _newComments.value = sync
                                        else _updatedComments.value = sync
                                    }
                                }
                                else -> {}
                            }
                        }
                        ActionType.DELETE -> {
                            when (msg.annotationType) {
                                AnnotationType.HIGHLIGHT -> {
                                    Handler(Looper.getMainLooper()).post {
                                        _deletedHighlights.value = msg.payload.id
                                    }
                                }
                                AnnotationType.COMMENT -> {
                                    Handler(Looper.getMainLooper()).post {
                                        _deletedComments.value = msg.payload.id
                                    }
                                }
                                else -> {}
                            }
                        }
                        ActionType.LEADERSHIP_TRANSFER -> {
                            // targetUserId 또는 currentHostId 필드 사용
                            val newHostId = msg.targetUserId.ifEmpty { msg.currentHostId }
                            Handler(Looper.getMainLooper()).post {
                                _leadershipTransfers.value = newHostId
                            }
                        }
                        else -> {}
                    }
                }



                override fun onError(t: Throwable) {
                    if (!shouldReconnect) return
                    isActive = false
                    _connectionStatus.postValue(PdfSyncConnectionStatus.ERROR("연결 오류: ${t.message}"))
                    scheduleReconnect()
                }

                override fun onCompleted() {
                    isActive = false
                    _connectionStatus.postValue(PdfSyncConnectionStatus.DISCONNECTED)
                    scheduleReconnect()
                }

            })

            backoffMs = 1_000L

            _connectionStatus.value = PdfSyncConnectionStatus.CONNECTED

            sendJoinRequest()

        } catch (e: Exception) {
            Log.e(TAG, "gRPC 연결 예외", e)
            _connectionStatus.value = PdfSyncConnectionStatus.ERROR("연결 실패: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        Handler(Looper.getMainLooper()).postDelayed({
            val gid = groupId; val uid = userId
            if (gid != null && uid != null) connectAndJoinRoom(gid, uid)
            // 다음 백오프 준비
        }, backoffMs)
        backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
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

    override fun transferLeadership(groupId: Long, fromUserId: String, toUserId: String) {
        val msg = SyncMessage.newBuilder()
            .setGroupId(groupId)
            .setUserId(fromUserId)
            .setActionType(ActionType.LEADERSHIP_TRANSFER)
            .setAnnotationType(AnnotationType.NONE)
            .setTargetUserId(toUserId)
            .setCurrentHostId(toUserId)
            .build()
        requestObserver?.onNext(msg)
    }

    // JOIN_ROOM 요청 헬퍼
    override fun sendJoinRequest() {
        requestObserver?.onNext(
            SyncMessage.newBuilder()
                .setGroupId(groupId!!)
                .setUserId(userId!!)
                .setActionType(ActionType.JOIN_ROOM)
                .build()
        )
    }

    override fun sendAnnotation(
        groupId: Long,
        userId: String,
        type: AnnotationType,
        payload: AnnotationPayload,
        actionType: ActionType
    ) {
        val obs = requestObserver ?: run {
            Log.w(TAG, "stream not open")
            return
        }
        if (!isActive) return
        val msg = SyncMessage.newBuilder()
            .setGroupId(groupId)
            .setUserId(userId)
            .setActionType(actionType)
            .setAnnotationType(type)
            .setPayload(payload)
            .build()
        obs.onNext(msg)
    }

    override fun sendProgressUpdate(groupId: Long, userId: String, page: Int) {
        val obs = requestObserver ?: return
        if (!isActive) return
        val msg = SyncMessage.newBuilder()
            .setGroupId(groupId)
            .setUserId(userId)
            .setActionType(ActionType.PROGRESS_UPDATE)
            .setAnnotationType(AnnotationType.NONE)
            .setPayload(AnnotationPayload.newBuilder().setPage(page).build())
            .build()
        obs.onNext(msg)
    }


    override fun reconnect() {
        if (!shouldReconnect) return
        disconnect()
        scheduleReconnect()
    }

    override fun leaveRoom() {
        shouldReconnect = false
        isActive = false
        requestObserver?.onCompleted()
        disconnect()
        _connectionStatus.value = PdfSyncConnectionStatus.DISCONNECTED
        runCatching { netCb?.let { cm.unregisterNetworkCallback(it) } }
        netCb = null
    }

    override fun isConnected(): Boolean =
        connectionStatus.value == PdfSyncConnectionStatus.CONNECTED && isActive

    private fun disconnect() {
        try { channel?.shutdownNow(); Log.d(TAG, "gRPC 연결 종료") }
        catch(e: Exception) { Log.e(TAG, "연결 종료 중 오류", e) }
        finally { channel = null; stub = null; requestObserver = null }
    }
}