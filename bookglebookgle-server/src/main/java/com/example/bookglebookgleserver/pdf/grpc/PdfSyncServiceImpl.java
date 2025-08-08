// src/main/java/com/example/bookglebookgleserver/pdf/grpc/PdfSyncServiceImpl.java
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
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
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

    @Override
    public StreamObserver<SyncMessage> sync(StreamObserver<SyncMessage> out) {
        return new StreamObserver<>() {
            Long groupId = null;
            String userId = null;
            LocalDateTime enterTime = null;

            @Override
            public void onNext(SyncMessage req) {
                try {
                    if (groupId == null) {
                        groupId = req.getGroupId();
                        userId = req.getUserId();
                        enterTime = LocalDateTime.now();

                        GroupState state = GroupStore.get(groupId);
                        state.lock.lock();
                        try {
                            // 관찰자/참가자 등록
                            state.observers.put(userId, out);
                            GroupState.ParticipantMeta meta =
                                    state.participants.computeIfAbsent(userId, GroupState.ParticipantMeta::new);

                            // ✅ 원조 호스트 식별
                            String origHostId = getOriginalHostId(groupId);
                            if (origHostId != null && origHostId.equals(userId)) {
                                meta.isOriginalHost = true;

                                // ✅ 현재 리더가 아니면 즉시 회수
                                if (!Objects.equals(state.currentLeaderId, userId)) {
                                    String prev = state.currentLeaderId; // 이전 리더(있을 수도/없을 수도)

                                    state.currentLeaderId = userId;

                                    // 브로드캐스트: 리더 변경
                                    SyncMessage evt = SyncMessage.newBuilder()
                                            .setGroupId(groupId)
                                            .setUserId(userId)                    // 이벤트 발신자(원조 호스트)
                                            .setActionType(ActionType.LEADERSHIP_TRANSFER)
                                            .setAnnotationType(AnnotationType.NONE)
                                            .setTargetUserId(userId)              // 새 리더
                                            .setCurrentHostId(userId)
                                            .build();
                                    broadcastToAll(state, evt);
                                }

                            } else {
                                // 원조 호스트가 없거나(이상 케이스) 아직 미입장인 경우:
                                // 리더가 비어 있으면 "첫 참가자"를 리더로 세움(백업 정책)
                                if (state.currentLeaderId == null) {
                                    state.currentLeaderId = userId;
                                }
                            }

                            // 최신 스냅샷 → 나에게
                            sendSnapshotTo(state, userId);
                            // (선택) 모두에게도 스냅샷
                            broadcastSnapshot(state);

                        } finally {
                            state.lock.unlock();
                        }
                    }

                    // 액션 처리
                    switch (req.getActionType()) {
	                    case JOIN_ROOM -> handleJoinRoom(req);
	                    case LEADERSHIP_TRANSFER -> handleLeadershipTransfer(req);
	                    case PAGE_MOVE -> handlePageMove(req);
	                    case ADD, UPDATE, DELETE -> {
	                        handleAnnotation(req);
	                        broadcastToAll(GroupStore.get(groupId), req);
	                    }
	                    case PARTICIPANTS -> {
	                        // ignore client-side snapshots
	                    }
	                    default -> {}
	                }



                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[PDF-SYNC] onNext error", e);
                    out.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.warning("[PDF-SYNC] channel error: " + t.getMessage());
                handleLeave();
            }

            @Override
            public void onCompleted() {
                recordViewingDuration();
                handleLeave();
                out.onCompleted();
                logger.info("[PDF-SYNC] channel completed");
            }

            // ---------- handlers ----------
            
            private void handleJoinRoom(SyncMessage req) {
                GroupState state = GroupStore.get(req.getGroupId());
                state.lock.lock();
                try {
                    state.participants.computeIfAbsent(req.getUserId(), GroupState.ParticipantMeta::new);
                    state.observers.put(req.getUserId(), out);

                    String origHostId = getOriginalHostId(state.groupId);
                    boolean isOriginalHost = (origHostId != null && origHostId.equals(req.getUserId()));
                    if (isOriginalHost) {
                        state.participants.get(req.getUserId()).isOriginalHost = true;

                        if (!Objects.equals(state.currentLeaderId, req.getUserId())) {
                            state.currentLeaderId = req.getUserId();

                            // 리더 변경 브로드캐스트
                            SyncMessage evt = SyncMessage.newBuilder()
                                    .setGroupId(state.groupId)
                                    .setUserId(req.getUserId())
                                    .setActionType(ActionType.LEADERSHIP_TRANSFER)
                                    .setAnnotationType(AnnotationType.NONE)
                                    .setTargetUserId(req.getUserId())
                                    .setCurrentHostId(req.getUserId())
                                    .build();
                            broadcastToAll(state, evt);
                        }
                    } else {
                        // 백업 정책: 리더 없음 → 아무나(보통 첫 입장자)
                        if (state.currentLeaderId == null || !state.participants.containsKey(state.currentLeaderId)) {
                            String next = state.participants.keySet().stream().findFirst().orElse(req.getUserId());
                            state.currentLeaderId = next;
                        }
                    }

                    sendSnapshotTo(state, req.getUserId());
                    broadcastSnapshot(state);
                } finally {
                    state.lock.unlock();
                }
            }


            private void handleLeadershipTransfer(SyncMessage req) {
                GroupState state = GroupStore.get(req.getGroupId());
                String from = req.getUserId();
                String to   = req.getTargetUserId();

                state.lock.lock();
                try {
                    boolean allowed = Objects.equals(from, state.currentLeaderId)
                            || (state.participants.get(from) != null && state.participants.get(from).isOriginalHost);
                    if (!allowed) return;
                    if (!state.participants.containsKey(to)) return;

                    state.currentLeaderId = to;

                    // 리더 변경 알림
                    SyncMessage evt = SyncMessage.newBuilder()
                            .setGroupId(state.groupId)
                            .setUserId(from)
                            .setActionType(ActionType.LEADERSHIP_TRANSFER)
                            .setAnnotationType(AnnotationType.NONE)
                            .setTargetUserId(to)
                            .setCurrentHostId(to)
                            .build();
                    broadcastToAll(state, evt);

                    // 최신 스냅샷도 배포(안전)
                    broadcastSnapshot(state);
                } finally {
                    state.lock.unlock();
                }
            }

            private void handlePageMove(SyncMessage req) {
                GroupState state = GroupStore.get(req.getGroupId());
                String uid = req.getUserId();
                int page = req.getPayload().getPage();
                if (page <= 0) return;

                // 리더만 반영
                if (!Objects.equals(uid, state.currentLeaderId)) return;

                // 상태 갱신
                state.currentPage = page;

                // 진도 저장(너희 서비스 로직 유지)
                try {
                    pdfService.updateOrInsertProgress(Long.valueOf(uid), state.groupId, page);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[PDF-SYNC] progress update failed", e);
                }

                // PAGE_MOVE 브로드캐스트(에코 포함)
                SyncMessage evt = SyncMessage.newBuilder()
                        .setGroupId(state.groupId)
                        .setUserId(uid)
                        .setActionType(ActionType.PAGE_MOVE)
                        .setAnnotationType(AnnotationType.PAGE)
                        .setPayload(AnnotationPayload.newBuilder().setPage(page).build())
                        .build();
                broadcastToAll(state, evt);
            }

            private void handleAnnotation(SyncMessage req) {
                try {
                    Group group = groupRepository.findById(groupId)
                            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 그룹입니다."));
                    String sender = req.getUserId();
                    AnnotationType type = req.getAnnotationType();
                    ActionType action = req.getActionType();
                    AnnotationPayload p = req.getPayload();

                    if (type == AnnotationType.HIGHLIGHT) {
                        if (action == ActionType.ADD) {
                            Highlight h = Highlight.builder()
                                    .group(group).userId(Long.valueOf(sender))
                                    .page(p.getPage()).snippet(p.getSnippet()).color(p.getColor())
                                    .startX(p.getCoordinates().getStartX()).startY(p.getCoordinates().getStartY())
                                    .endX(p.getCoordinates().getEndX()).endY(p.getCoordinates().getEndY())
                                    .build();
                            highlightRepository.save(h);
                        } else if (action == ActionType.UPDATE) {
                            highlightRepository.findById(p.getId()).ifPresent(h -> {
                                h.setSnippet(p.getSnippet());
                                h.setColor(p.getColor());
                                h.setStartX(p.getCoordinates().getStartX());
                                h.setStartY(p.getCoordinates().getStartY());
                                h.setEndX(p.getCoordinates().getEndX());
                                h.setEndY(p.getCoordinates().getEndY());
                                highlightRepository.save(h);
                            });
                        } else if (action == ActionType.DELETE) {
                            highlightRepository.deleteById(p.getId());
                        }
                    } else if (type == AnnotationType.COMMENT) {
                        if (action == ActionType.ADD) {
                            Comment c = Comment.builder()
                                    .group(group).userId(Long.valueOf(sender))
                                    .page(p.getPage()).snippet(p.getSnippet()).text(p.getText())
                                    .startX(p.getCoordinates().getStartX()).startY(p.getCoordinates().getStartY())
                                    .endX(p.getCoordinates().getEndX()).endY(p.getCoordinates().getEndY())
                                    .build();
                            commentRepository.save(c);
                        } else if (action == ActionType.UPDATE) {
                            commentRepository.findById(p.getId()).ifPresent(c -> {
                                c.setText(p.getText());
                                c.setSnippet(p.getSnippet());
                                c.setStartX(p.getCoordinates().getStartX());
                                c.setStartY(p.getCoordinates().getStartY());
                                c.setEndX(p.getCoordinates().getEndX());
                                c.setEndY(p.getCoordinates().getEndY());
                                commentRepository.save(c);
                            });
                        } else if (action == ActionType.DELETE) {
                            commentRepository.deleteById(p.getId());
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[PDF-SYNC] DB 처리 중 오류", e);
                }
            }

            private void handleLeave() {
                if (groupId == null || userId == null) return;
                GroupState state = GroupStore.get(groupId);
                state.lock.lock();
                try {
                    // 관찰자/참가자 제거
                    state.observers.remove(userId);
                    GroupState.ParticipantMeta removed = state.participants.remove(userId);

                    // 리더였으면 재선정
                    if (Objects.equals(userId, state.currentLeaderId)) {
                        String next = state.participants.values().stream()
                                .filter(pm -> pm.isOriginalHost)
                                .map(pm -> pm.userId)
                                .findFirst()
                                .orElseGet(() -> state.participants.keySet().stream().findFirst().orElse(null));
                        state.currentLeaderId = next;

                        if (next != null) {
                            SyncMessage evt = SyncMessage.newBuilder()
                                    .setGroupId(groupId)
                                    .setUserId(userId)
                                    .setActionType(ActionType.LEADERSHIP_TRANSFER)
                                    .setAnnotationType(AnnotationType.NONE)
                                    .setTargetUserId(next)
                                    .setCurrentHostId(next)
                                    .build();
                            broadcastToAll(state, evt);
                        }
                    }

                    // 최신 스냅샷 브로드캐스트
                    broadcastSnapshot(state);
                } finally {
                    state.lock.unlock();
                }
            }

            // ---------- helpers ----------

            private ParticipantsSnapshot buildSnapshot(GroupState state) {
                List<Participant> list = new ArrayList<>();
                for (GroupState.ParticipantMeta pm : state.participants.values()) {
                    list.add(pm.toProto(Objects.equals(pm.userId, state.currentLeaderId)));
                }
                return ParticipantsSnapshot.newBuilder()
                        .addAllParticipants(list)
                        .setCurrentPage(state.currentPage) // ✅ proto에 추가한 필드
                        .build();
            }

            private void sendSnapshotTo(GroupState state, String targetUserId) {
                StreamObserver<SyncMessage> o = state.observers.get(targetUserId);
                if (o == null) return;
                safeOnNext(o, SyncMessage.newBuilder()
                        .setGroupId(state.groupId)
                        .setUserId(targetUserId)
                        .setActionType(ActionType.PARTICIPANTS)
                        .setParticipants(buildSnapshot(state))
                        .build());
            }

            private void broadcastSnapshot(GroupState state) {
                SyncMessage msg = SyncMessage.newBuilder()
                        .setGroupId(state.groupId)
                        .setActionType(ActionType.PARTICIPANTS)
                        .setParticipants(buildSnapshot(state))
                        .build();
                broadcastToAll(state, msg);
            }

            private void broadcastToAll(GroupState state, SyncMessage msg) {
                for (StreamObserver<SyncMessage> o : state.observers.values()) {
                    safeOnNext(o, msg);
                }
            }

            private void safeOnNext(StreamObserver<SyncMessage> o, SyncMessage msg) {
                try { o.onNext(msg); } catch (Exception ignore) { /* 끊긴 스트림은 onError/onCompleted에서 정리됨 */ }
            }

            private void recordViewingDuration() {
                if (enterTime == null || groupId == null || userId == null) return;
                try {
                    LocalDateTime exitTime = LocalDateTime.now();
                    long seconds = Duration.between(enterTime, exitTime).getSeconds();
                    viewingSessionService.saveSession(Long.valueOf(userId), groupId, enterTime, exitTime, seconds);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[PDF-SYNC] viewing session save failed", e);
                }
            }
            
            private String getOriginalHostId(long groupId) {
                return groupRepository.findById(groupId)
                        .map(g -> {
                            // TODO: 너희 Group 엔티티에 맞게 수정 ↓↓↓
                            Long ownerId = (g.getHostUser() != null) ? g.getHostUser().getId() : null; // 예: 생성자/방장 유저 ID
                            return ownerId != null ? String.valueOf(ownerId) : null;
                        })
                        .orElse(null);
            }

        };
    }
}
