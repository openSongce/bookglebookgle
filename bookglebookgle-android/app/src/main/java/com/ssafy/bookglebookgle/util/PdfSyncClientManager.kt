package com.ssafy.bookglebookgle.util

import android.util.Log
import com.ssafy.bookglebookgle.grpc.Ack
import com.ssafy.bookglebookgle.grpc.JoinRequest
import com.ssafy.bookglebookgle.grpc.PdfSyncServiceGrpc
import com.ssafy.bookglebookgle.grpc.SyncMessage
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver

object PdfSyncClientManager {
    private var channel: ManagedChannel? = null
    private var stub: PdfSyncServiceGrpc.PdfSyncServiceStub? = null

    fun connect(groupId: Long, userId: String, onReceive: (SyncMessage) -> Unit) {
        channel = ManagedChannelBuilder
            .forAddress("52.79.59.66", 6565)
            .usePlaintext()
            .build()

        stub = PdfSyncServiceGrpc.newStub(channel)

        val request = JoinRequest.newBuilder()
            .setGroupId(groupId)
            .setUserId(userId)
            .build()

        stub!!.joinRoom(request, object : StreamObserver<SyncMessage> {
            override fun onNext(value: SyncMessage) {
                onReceive(value)
            }

            override fun onError(t: Throwable) {
                Log.e("PdfSync", "Stream error: ${t.message}")
            }

            override fun onCompleted() {
                Log.d("PdfSync", "Stream closed")
            }
        })
    }

    fun sendPageUpdate(userId: String, page: Int) {
        val message = SyncMessage.newBuilder()
            .setUserId(userId)
            .setCurrentPage(page)
            .build()
        stub?.sendMessage(message, object : StreamObserver<Ack> {
            override fun onNext(value: Ack) {
                Log.d("PdfSync", "Ack: ${value.message}")
            }

            override fun onError(t: Throwable) {
                Log.e("PdfSync", "Send error: ${t.message}")
            }

            override fun onCompleted() {}
        })
    }

    fun disconnect() {
        channel?.shutdownNow()
        channel = null
    }
}
