package com.example.bookglebookgleserver.pdf.grpc;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@GrpcService
public class PdfSyncServiceImpl extends PdfSyncServiceGrpc.PdfSyncServiceImplBase {

    private static final Logger logger = Logger.getLogger(PdfSyncServiceImpl.class.getName());

    // 그룹 ID별로 연결된 클라이언트 스트림 저장
    private final ConcurrentHashMap<Long, List<StreamObserver<SyncMessage>>> sessions = new ConcurrentHashMap<>();

    @Override
    public void joinRoom(JoinRequest request, StreamObserver<SyncMessage> responseObserver) {
        long groupId = request.getGroupId();
        String userId = request.getUserId();

        sessions.putIfAbsent(groupId, new CopyOnWriteArrayList<>());
        sessions.get(groupId).add(responseObserver);

        logger.info("JoinRoom 요청 수신: groupId=" + groupId + ", userId=" + userId);
        logger.info("현재 세션 수 (groupId=" + groupId + "): " + sessions.get(groupId).size());

        if (responseObserver instanceof ServerCallStreamObserver) {
            ServerCallStreamObserver<SyncMessage> serverObserver = (ServerCallStreamObserver<SyncMessage>) responseObserver;
            serverObserver.setOnCancelHandler(() -> {
                sessions.get(groupId).remove(responseObserver);
                logger.warning("연결 끊김 감지: groupId=" + groupId + ", userId=" + userId);
                logger.info("남은 세션 수 (groupId=" + groupId + "): " + sessions.get(groupId).size());
            });
        }
    }

    @Override
    public void sendMessage(SyncMessage request, StreamObserver<Ack> responseObserver) {
        long groupId = request.getGroupId();
        String senderId = request.getUserId();

        logger.info("메시지 수신: groupId=" + groupId + ", userId=" + senderId + ", page=" + request.getCurrentPage());

        List<StreamObserver<SyncMessage>> observers = sessions.get(groupId);

        if (observers == null || observers.isEmpty()) {
            logger.warning("[경고] 수신자 없음: groupId=" + groupId);
        } else {
            logger.info("브로드캐스트 시작: 총 " + observers.size() + "명에게 송신");

            for (StreamObserver<SyncMessage> observer : observers) {
                try {
                    observer.onNext(request);
                    logger.info("메시지 송신 성공 → userId=" + senderId);
                } catch (Exception e) {
                    observers.remove(observer);
                    logger.log(Level.WARNING, "메시지 송신 실패: observer 제거됨", e);
                }
            }
        }

        Ack ack = Ack.newBuilder()
                .setSuccess(true)
                .setMessage("Broadcast complete from " + senderId)
                .build();

        try {
            responseObserver.onNext(ack);
            responseObserver.onCompleted();
            logger.info("ACK 응답 완료: " + ack.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ACK 응답 중 오류 발생", e);
        }
    }
}
