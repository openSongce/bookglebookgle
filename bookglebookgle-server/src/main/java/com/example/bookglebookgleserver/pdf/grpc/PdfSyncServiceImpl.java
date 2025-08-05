package com.example.bookglebookgleserver.pdf.grpc;

import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.bookglebookgleserver.pdf.grpc.PdfSyncServiceGrpc;
import com.example.bookglebookgleserver.pdf.grpc.SyncMessage;
import com.example.bookglebookgleserver.pdf.grpc.JoinRequest;
import com.example.bookglebookgleserver.pdf.grpc.Ack;

public class PdfSyncServiceImpl extends PdfSyncServiceGrpc.PdfSyncServiceImplBase {

	private final ConcurrentHashMap<Long, List<StreamObserver<SyncMessage>>> sessions = new ConcurrentHashMap<>();

    @Override
    public void joinRoom(JoinRequest request, StreamObserver<SyncMessage> responseObserver) {
        long groupId = request.getGroupId();
        sessions.putIfAbsent(groupId, new CopyOnWriteArrayList<>());
        sessions.get(groupId).add(responseObserver);
        System.out.println("✅ JoinRoom: " + request.getUserId());
    }

    @Override
    public void sendMessage(SyncMessage request, StreamObserver<Ack> responseObserver) {
        // broadcast
        for (List<StreamObserver<SyncMessage>> observers : sessions.values()) {
            for (StreamObserver<SyncMessage> observer : observers) {
                observer.onNext(request);
            }
        }

        Ack ack = Ack.newBuilder().setMessage("Broadcast complete").build();
        responseObserver.onNext(ack);
        responseObserver.onCompleted();
    }
}
