package com.ssafy.bookglebookgle.repository

import androidx.lifecycle.LiveData

interface PdfGrpcRepository {

    val newPageUpdates: LiveData<PdfPageSync>

    /** 현재 gRPC 연결 상태 */
    val connectionStatus: LiveData<PdfSyncConnectionStatus>

    /** 방에 연결해서 업데이트 스트림 열기 */
    fun connectAndJoinRoom(groupId: Long, userId: String)

    /** 현재 페이지를 서버에 전송 */
    fun sendPageUpdate(page: Int)

    /** 명시적으로 방 나가기 및 연결 종료 */
    fun leaveRoom()

    /** (옵션) 연결 재시도 */
    fun reconnect()

    /** 연결 여부 확인 */
    fun isConnected(): Boolean
}