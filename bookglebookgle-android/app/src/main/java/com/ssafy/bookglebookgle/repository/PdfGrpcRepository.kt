package com.ssafy.bookglebookgle.repository

import androidx.lifecycle.LiveData
import com.example.bookglebookgleserver.pdf.grpc.ActionType
import com.example.bookglebookgleserver.pdf.grpc.AnnotationPayload
import com.example.bookglebookgleserver.pdf.grpc.AnnotationType
import com.example.bookglebookgleserver.pdf.grpc.ParticipantsSnapshot
import com.ssafy.bookglebookgle.entity.CommentSync
import com.ssafy.bookglebookgle.entity.HighlightSync
import com.ssafy.bookglebookgle.entity.Participant
import com.ssafy.bookglebookgle.entity.PdfPageSync
import com.example.bookglebookgleserver.pdf.grpc.Participant as ProtoParticipant


interface PdfGrpcRepository {

    val newPageUpdates: LiveData<PdfPageSync>
    val newHighlights: LiveData<HighlightSync>
    val newComments: LiveData<CommentSync>
    val updatedHighlights: LiveData<HighlightSync>
    val updatedComments: LiveData<CommentSync>
    val deletedHighlights: LiveData<Long>
    val deletedComments: LiveData<Long>
    val leadershipTransfers: LiveData<String>
    val joinRequests: LiveData<String>
    val participantsSnapshot: LiveData<ParticipantsSnapshot>
    val progressUpdates: LiveData<Pair<String, Int>>


    /** 현재 gRPC 연결 상태 */
    val connectionStatus: LiveData<PdfSyncConnectionStatus>

    /** 방에 연결해서 업데이트 스트림 열기 */
    fun connectAndJoinRoom(groupId: Long, userId: String)

    /** 현재 페이지를 서버에 전송 */
    fun sendPageUpdate(page: Int)

    /** Annotation 서버에 전송 */
    fun sendAnnotation(
        groupId: Long,
        userId: String,
        type: AnnotationType,
        payload: AnnotationPayload,
        actionType: ActionType = ActionType.ADD
    )

    /** 명시적으로 방 나가기 및 연결 종료 */
    fun leaveRoom()

    /** (옵션) 연결 재시도 */
    fun reconnect()

    /** 연결 여부 확인 */
    fun isConnected(): Boolean

    fun transferLeadership(groupId: Long, fromUserId: String, toUserId: String)

    fun sendJoinRequest()

    fun sendProgressUpdate(groupId: Long, userId: String, page: Int)

}