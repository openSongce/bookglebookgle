package com.example.bookglebookgleserver.lastPage;// src/test/java/.../GrpcSyncClientTestMain.java
import com.example.bookglebookgleserver.pdf.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class GrpcSyncClientTestMain {
    public static void main(String[] args) throws Exception {
        ManagedChannel ch = ManagedChannelBuilder
                .forAddress("localhost", 8081)
                .usePlaintext()
                .build();

        PdfSyncServiceGrpc.PdfSyncServiceStub stub = PdfSyncServiceGrpc.newStub(ch);
        StreamObserver<SyncMessage> out = stub.sync(new StreamObserver<>() {
            public void onNext(SyncMessage value) { System.out.println("[SERVER] " + value); }
            public void onError(Throwable t) { t.printStackTrace(); }
            public void onCompleted() { System.out.println("completed"); }
        });

        // JOIN
        out.onNext(SyncMessage.newBuilder()
                .setGroupId(3L).setUserId("1").setActionType(ActionType.JOIN_ROOM).build());

        // PAGE_MOVE(리더일 때만 DB 반영)
        out.onNext(SyncMessage.newBuilder()
                .setGroupId(3L).setUserId("1")
                .setActionType(ActionType.PAGE_MOVE)
                .setAnnotationType(AnnotationType.PAGE)
                .setPayload(AnnotationPayload.newBuilder().setPage(12).build())
                .build());

        // PROGRESS_UPDATE(리더 아니어도 반영)
        out.onNext(SyncMessage.newBuilder()
                .setGroupId(3L).setUserId("1")
                .setActionType(ActionType.PROGRESS_UPDATE)
                .setPayload(AnnotationPayload.newBuilder().setPage(20).build())
                .build());

        Thread.sleep(500); // 응답 대기
        out.onCompleted();
        ch.shutdownNow();
    }
}
