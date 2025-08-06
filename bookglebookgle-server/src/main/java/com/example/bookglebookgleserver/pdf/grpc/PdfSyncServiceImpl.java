package com.example.bookglebookgleserver.pdf.grpc;

import bgbg.pdf.*;
import com.example.bookglebookgleserver.highlight.entity.Highlight;
import com.example.bookglebookgleserver.highlight.repository.HighlightRepository;
import com.example.bookglebookgleserver.comment.entity.Comment;
import com.example.bookglebookgleserver.comment.repository.CommentRepository;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@GrpcService
@RequiredArgsConstructor
public class PdfSyncServiceImpl extends PdfSyncServiceGrpc.PdfSyncServiceImplBase {

    private static final Logger logger = Logger.getLogger(PdfSyncServiceImpl.class.getName());

    private final HighlightRepository highlightRepository;
    private final CommentRepository commentRepository;

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

        // 연결이 끊어졌을 때 세션 관리 (예: 브라우저 닫기, 앱 종료 등)
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
        ActionType actionType = request.getActionType();
        AnnotationType annotationType = request.getAnnotationType();
        AnnotationPayload payload = request.getPayload();

        // 1. 동작/타입별 실시간 협업 + DB 반영
        if (annotationType == AnnotationType.HIGHLIGHT) {
            if (actionType == ActionType.ADD) {
                logger.info("[하이라이트 추가] page=" + payload.getPage() + ", color=" + payload.getColor());
                Highlight highlight = Highlight.builder()
                        .groupId(groupId)
                        .userId(Long.valueOf(senderId))
                        .page(payload.getPage())
                        .snippet(payload.getSnippet())
                        .color(payload.getColor())
                        .startX(payload.getCoordinates().getStartX())
                        .startY(payload.getCoordinates().getStartY())
                        .endX(payload.getCoordinates().getEndX())
                        .endY(payload.getCoordinates().getEndY())
                        .build();
                highlightRepository.save(highlight);
            } else if (actionType == ActionType.UPDATE) {
                logger.info("[하이라이트 수정] id=" + payload.getId());
                Optional<Highlight> opt = highlightRepository.findById(payload.getId());
                opt.ifPresent(highlight -> {
                    highlight.setSnippet(payload.getSnippet());
                    highlight.setColor(payload.getColor());
                    highlight.setStartX(payload.getCoordinates().getStartX());
                    highlight.setStartY(payload.getCoordinates().getStartY());
                    highlight.setEndX(payload.getCoordinates().getEndX());
                    highlight.setEndY(payload.getCoordinates().getEndY());
                    highlightRepository.save(highlight);
                });
            } else if (actionType == ActionType.DELETE) {
                logger.info("[하이라이트 삭제] id=" + payload.getId());
                highlightRepository.deleteById(payload.getId());
            }
        } else if (annotationType == AnnotationType.COMMENT) {
            if (actionType == ActionType.ADD) {
                logger.info("[주석 추가] page=" + payload.getPage() + ", text=" + payload.getText());
                Comment comment = Comment.builder()
                        .groupId(groupId)
                        .userId(Long.valueOf(senderId))
                        .page(payload.getPage())
                        .snippet(payload.getSnippet())
                        .text(payload.getText())
                        .startX(payload.getCoordinates().getStartX())
                        .startY(payload.getCoordinates().getStartY())
                        .endX(payload.getCoordinates().getEndX())
                        .endY(payload.getCoordinates().getEndY())
                        .build();
                commentRepository.save(comment);
            } else if (actionType == ActionType.UPDATE) {
                logger.info("[주석 수정] id=" + payload.getId());
                Optional<Comment> opt = commentRepository.findById(payload.getId());
                opt.ifPresent(comment -> {
                    comment.setText(payload.getText());
                    comment.setSnippet(payload.getSnippet());
                    comment.setStartX(payload.getCoordinates().getStartX());
                    comment.setStartY(payload.getCoordinates().getStartY());
                    comment.setEndX(payload.getCoordinates().getEndX());
                    comment.setEndY(payload.getCoordinates().getEndY());
                    commentRepository.save(comment);
                });
            } else if (actionType == ActionType.DELETE) {
                logger.info("[주석 삭제] id=" + payload.getId());
                commentRepository.deleteById(payload.getId());
            }
        } else if (annotationType == AnnotationType.PAGE) {
            if (actionType == ActionType.PAGE_MOVE) {
                logger.info("[페이지 이동] page=" + payload.getPage());
                // 페이지 이동은 DB 저장 불필요, 실시간 브로드캐스트만
            }
        } else {
            logger.info("[알 수 없는 타입] actionType=" + actionType + ", annotationType=" + annotationType);
        }

        // 2. 모든 참여자에게 브로드캐스트
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

        // 3. ACK 응답
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
