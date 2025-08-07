package com.example.bookglebookgleserver.pdf.grpc;

import com.example.bookglebookgleserver.comment.entity.Comment;
import com.example.bookglebookgleserver.comment.repository.CommentRepository;
import com.example.bookglebookgleserver.group.entity.Group;
import com.example.bookglebookgleserver.group.repository.GroupRepository;
import com.example.bookglebookgleserver.highlight.entity.Highlight;
import com.example.bookglebookgleserver.highlight.repository.HighlightRepository;
import com.example.bookglebookgleserver.pdf.repository.PdfReadingProgressRepository;
import com.example.bookglebookgleserver.pdf.service.PdfService;
import com.example.bookglebookgleserver.user.service.ViewingSessionService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

@GrpcService
@RequiredArgsConstructor
public class PdfSyncServiceImpl extends PdfSyncServiceGrpc.PdfSyncServiceImplBase {

    private static final Logger logger = Logger.getLogger(PdfSyncServiceImpl.class.getName());

    private final HighlightRepository highlightRepository;
    private final CommentRepository commentRepository;
    private final GroupRepository groupRepository;
    private final PdfService pdfService;
    private final ViewingSessionService viewingSessionService;

    // 그룹별로 연결된 클라이언트 스트림 관리
    private final ConcurrentHashMap<Long, Set<StreamObserver<SyncMessage>>> sessions = new ConcurrentHashMap<>();

    @Override
    public StreamObserver<SyncMessage> sync(StreamObserver<SyncMessage> responseObserver) {
        return new StreamObserver<SyncMessage>() {
            private Long groupId = null;
            private LocalDateTime enterTime;
            private String senderId;

            @Override
            public void onNext(SyncMessage request) {
                if (groupId == null) {
                    groupId = request.getGroupId();
                    senderId=request.getUserId();
                    enterTime= LocalDateTime.now();
                    sessions.computeIfAbsent(groupId, k -> new CopyOnWriteArraySet<>()).add(responseObserver);
                    logger.info("[PDF-SYNC] 그룹 " + groupId + " 연결! 현재 세션: " + sessions.get(groupId).size());
                }

                String senderId = request.getUserId();
                ActionType actionType = request.getActionType();
                AnnotationType annotationType = request.getAnnotationType();
                AnnotationPayload payload = request.getPayload();

                // 1. DB 처리 (주석/하이라이트)
                try {
                    // 공통: groupId로 Group 객체를 조회
                    Group group = groupRepository.findById(groupId)
                            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 그룹입니다."));

                    if (annotationType == AnnotationType.HIGHLIGHT) {
                        if (actionType == ActionType.ADD) {
                            logger.info("[하이라이트 추가] page=" + payload.getPage() + ", color=" + payload.getColor());
                            Highlight highlight = Highlight.builder()
                                    .group(group)
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
                                    .group(group)
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

                            int movedPage = payload.getPage();
                            Long userIdLong = Long.valueOf(senderId);

                            // 👉 진도 갱신 로직 호출 (없으면 insert, 있으면 update)
                            pdfService.updateOrInsertProgress(userIdLong, groupId, movedPage);

                            logger.info("[진도 갱신] userId=" + userIdLong + ", page=" + movedPage);
                        }
                    } else {
                        logger.info("[알 수 없는 타입] actionType=" + actionType + ", annotationType=" + annotationType);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[PDF-SYNC] DB 처리 중 오류", e);
                }

                // 2. 모든 참여자에게 브로드캐스트 (본인 포함)
                Set<StreamObserver<SyncMessage>> observers = sessions.getOrDefault(groupId, Set.of());
                logger.info("브로드캐스트: groupId=" + groupId + " 참여자=" + observers.size());
                for (StreamObserver<SyncMessage> observer : observers) {
                    try {
                        observer.onNext(request);
                        logger.info("→ 메시지 송신: userId=" + senderId);
                    } catch (Exception e) {
                        observers.remove(observer);
                        logger.log(Level.WARNING, "[PDF-SYNC] 메시지 송신 실패(제거됨)", e);
                        try { observer.onCompleted(); } catch (Exception ignore) {}
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                removeObserver();
                logger.warning("[PDF-SYNC] 클라이언트 채널 오류: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                recordViewingDuration();
                removeObserver();
                responseObserver.onCompleted();
                logger.info("[PDF-SYNC] 클라이언트 채널 정상 종료");
            }

            private void removeObserver() {
                if (groupId != null) {
                    Set<StreamObserver<SyncMessage>> observers = sessions.get(groupId);
                    if (observers != null) {
                        observers.remove(responseObserver);
                        logger.info("[PDF-SYNC] group " + groupId + "에서 클라이언트 연결 해제! 남은 접속 수: " + observers.size());
                    }
                }
            }

            private void recordViewingDuration() {
                if (enterTime != null && groupId != null) {
                    LocalDateTime exitTime = LocalDateTime.now();
                    Duration duration = Duration.between(enterTime, exitTime);
                    long seconds = duration.getSeconds();
                    logger.info("📊 userId=" + senderId + ", groupId=" + groupId + ", 활동 시간=" + seconds + "초");

                    // ✅ DB에 저장 (예: PdfViewingSession 테이블)
                    viewingSessionService.saveSession(Long.valueOf(senderId), groupId, enterTime, exitTime, seconds);
                }
            }

        };
    }
}
