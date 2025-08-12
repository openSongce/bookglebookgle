package com.example.bookglebookgleserver.chat.grpc;

import com.bgbg.ai.grpc.AIServiceProto.ChatMessageResponse;
import com.bgbg.ai.grpc.AIServiceProto.DiscussionInitResponse;
import com.example.bookglebookgleserver.chat.ChatMessage;
import com.example.bookglebookgleserver.chat.ChatServiceGrpc;
import com.example.bookglebookgleserver.chat.QuizEnd;
import com.example.bookglebookgleserver.chat.QuizQuestion;
import com.example.bookglebookgleserver.chat.entity.ChatRoom;
import com.example.bookglebookgleserver.chat.repository.ChatMessageRepository;
import com.example.bookglebookgleserver.chat.repository.ChatRoomRepository;
import com.example.bookglebookgleserver.user.entity.User;
import com.example.bookglebookgleserver.user.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final AiServiceClient aiServiceClient;

    // 채팅방별 클라이언트 스트림
    private final ConcurrentHashMap<Long, Set<StreamObserver<ChatMessage>>> roomObservers = new ConcurrentHashMap<>();
    // 토론 활성 상태
    private final ConcurrentHashMap<Long, Boolean> discussionActiveMap = new ConcurrentHashMap<>();
    // 진행 중 퀴즈 러너
    private final ConcurrentHashMap<Long, QuizRunner> quizRunners = new ConcurrentHashMap<>();
    // 스케줄러
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public StreamObserver<ChatMessage> chat(StreamObserver<ChatMessage> responseObserver) {
        return new StreamObserver<>() {
            private Long groupId = null;

            @Override
            public void onNext(ChatMessage message) {
                try {
                    if (groupId == null) {
                        groupId = message.getGroupId();
                        roomObservers.computeIfAbsent(groupId, k -> new CopyOnWriteArraySet<>()).add(responseObserver);
                        log.info("[gRPC-Chat] 그룹 {} 연결. 현재 접속 {}", groupId, roomObservers.get(groupId).size());
                    }

                    String msgType = message.getType();

                    // ===== 퀴즈: 중간 시작 =====
                    if ("QUIZ_MIDTERM_START".equals(msgType)) {
                        try {
                            QuizStartParam p = parseQuizStart(message);
                            if (!String.valueOf(groupId).equals(p.meetingId())) {
                                broadcastSystem(groupId, "퀴즈 시작 실패: meetingId가 방 ID와 다릅니다.");
                                return;
                            }
                            startPhase(groupId, message.getSenderId(), p, "MIDTERM");
                        } catch (Exception e) {
                            log.warn("[QUIZ_MIDTERM_START] 실패: {}", e.getMessage(), e);
                            broadcastSystem(groupId, "중간 퀴즈 시작 실패: " + e.getMessage());
                        }
                        return;
                    }

                    // ===== 퀴즈: 기말 시작 =====
                    if ("QUIZ_FINAL_START".equals(msgType)) {
                        try {
                            QuizStartParam p = parseQuizStart(message);
                            if (!String.valueOf(groupId).equals(p.meetingId())) {
                                broadcastSystem(groupId, "퀴즈 시작 실패: meetingId가 방 ID와 다릅니다.");
                                return;
                            }
                            startPhase(groupId, message.getSenderId(), p, "FINAL");
                        } catch (Exception e) {
                            log.warn("[QUIZ_FINAL_START] 실패: {}", e.getMessage(), e);
                            broadcastSystem(groupId, "기말 퀴즈 시작 실패: " + e.getMessage());
                        }
                        return;
                    }

                    // ===== 퀴즈: 강제 종료 =====
                    if ("QUIZ_CANCEL".equals(msgType)) {
                        stopQuiz(groupId, "CANCELLED");
                        broadcastToRoom(groupId, message);
                        return;
                    }

                    // ===== 퀴즈: 답안 제출 =====
                    if ("QUIZ_ANSWER".equals(msgType)) {
                        var qa = message.getQuizAnswer();
                        var runner = quizRunners.get(groupId);
                        if (runner != null) {
                            runner.handleAnswer(
                                    message.getSenderId(),
                                    qa.getQuizId(),
                                    qa.getQuestionIndex(),
                                    qa.getSelectedIndex()
                            );
                        }
                        broadcastToRoom(groupId, message);
                        return;
                    }

                    // ===== 토론: 시작/종료/중재 =====
                    if ("DISCUSSION_START".equals(msgType)) {
                        discussionActiveMap.put(groupId, true);
                        try {
                            DiscussionInitResponse resp = aiServiceClient.initializeDiscussion(groupId);
                            if (!resp.getSuccess()) {
                                discussionActiveMap.remove(groupId);
                                broadcastSystem(groupId, "토론 시작 실패: " + resp.getMessage());
                                return;
                            }
                            ChatMessage topicsMsg = ChatMessage.newBuilder()
                                    .setGroupId(groupId)
                                    .setSenderId(0L)
                                    .setSenderName("AI")
                                    .setTimestamp(System.currentTimeMillis())
                                    .setType("AI_RESPONSE")
                                    .setAiResponse(resp.getRecommendedTopic())
                                    .addAllSuggestedTopics(resp.getDiscussionTopicsList())
                                    .build();
                            broadcastToRoom(groupId, topicsMsg);
                        } catch (Exception e) {
                            discussionActiveMap.remove(groupId);
                            broadcastSystem(groupId, "토론 시작 실패: 서버 오류");
                        }
                        return;
                    } else if ("DISCUSSION_END".equals(msgType)) {
                        discussionActiveMap.remove(groupId);
                        try { aiServiceClient.endDiscussion(groupId); } catch (Exception ignore) {}
                        broadcastToRoom(groupId, message);
                        return;
                    } else if (isDiscussionActive(groupId)) {
                        broadcastToRoom(groupId, message);
                        aiServiceClient.streamChatModeration(
                                groupId, message.getSenderId(), message.getSenderName(), message.getContent(),
                                new StreamObserver<ChatMessageResponse>() {
                                    @Override
                                    public void onNext(ChatMessageResponse aiResp) {
                                        ChatMessage aiMsg = ChatMessage.newBuilder()
                                                .setGroupId(groupId)
                                                .setSenderId(0L)
                                                .setSenderName("AI")
                                                .setTimestamp(System.currentTimeMillis())
                                                .setType("AI_RESPONSE")
                                                .setAiResponse(aiResp.getAiResponse())
                                                .addAllSuggestedTopics(aiResp.getSuggestedTopicsList())
                                                .build();
                                        broadcastToRoom(groupId, aiMsg);
                                    }
                                    @Override public void onError(Throwable t) { log.error("AI 응답 오류", t); }
                                    @Override public void onCompleted() {}
                                }
                        );
                        return;
                    }

                    // ===== 일반 채팅 =====
                    saveAndBroadcastNormalMessage(message, groupId);

                } catch (Exception e) {
                    log.error("[gRPC-Chat] onNext 예외", e);
                }
            }

            private void saveAndBroadcastNormalMessage(ChatMessage message, Long groupId) {
                ChatRoom chatRoom = chatRoomRepository.findById(groupId).orElse(null);
                if (chatRoom == null) return;
                User sender = userRepository.findById(message.getSenderId()).orElse(null);
                if (sender == null) return;

                try {
                    com.example.bookglebookgleserver.chat.entity.ChatMessage entity =
                            com.example.bookglebookgleserver.chat.entity.ChatMessage.builder()
                                    .chatRoom(chatRoom)
                                    .sender(sender)
                                    .content(message.getContent())
                                    .createdAt(Instant.ofEpochMilli(message.getTimestamp())
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime())
                                    .build();
                    chatMessageRepository.save(entity);
                } catch (Exception ex) {
                    log.error("[gRPC-Chat] 메시지 저장 에러: {}", ex.getMessage(), ex);
                }
                broadcastToRoom(groupId, message);
            }

            @Override
            public void onError(Throwable t) {
                log.error("[gRPC-Chat] 채널 오류: {}", t.getMessage(), t);
                removeObserver();
            }

            @Override
            public void onCompleted() {
                removeObserver();
                responseObserver.onCompleted();
            }

            private void removeObserver() {
                if (groupId != null) {
                    Set<StreamObserver<ChatMessage>> observers = roomObservers.get(groupId);
                    if (observers != null) {
                        observers.remove(responseObserver);
                        log.info("[gRPC-Chat] 그룹 {} 구독 해제. 남은 접속 {}", groupId, observers.size());
                    }
                }
            }

            private boolean isDiscussionActive(Long groupId) {
                return discussionActiveMap.getOrDefault(groupId, false);
            }
        };
    }

    // ===== 페이즈 시작: 매번 AI 서버에 요청해서 2문제만 진행 =====
    private void startPhase(Long groupId, Long senderId, QuizStartParam p, String phase) {
        // (선택) 동시 진행 방지
        if (quizRunners.containsKey(groupId)) {
            broadcastSystem(groupId, "이미 진행 중인 퀴즈가 있습니다.");
            return;
        }

        // ✅ 페이즈마다 AI 서버에 별도 요청
        var result = aiServiceClient.generateQuiz(groupId, p.progressPercentage());
        var items = result.items();
        if (items == null || items.isEmpty()) {
            broadcastSystem(groupId, "문제를 불러오지 못했습니다.");
            return;
        }

        // ✅ 2문제만 사용 (넘치면 자르고, 부족하면 있는 만큼)
        List<AiServiceClient.QuizItem> phaseItems =
                items.size() > 2 ? new ArrayList<>(items.subList(0, 2)) : new ArrayList<>(items);

        // 시작 브로드캐스트 (proto 변경 없이 QuizStart 재사용)
        ChatMessage startMsg = ChatMessage.newBuilder()
                .setGroupId(groupId)
                .setType("QUIZ_" + phase + "_START") // QUIZ_MIDTERM_START / QUIZ_FINAL_START
                .setTimestamp(System.currentTimeMillis())
                .setQuizStart(com.example.bookglebookgleserver.chat.QuizStart.newBuilder()
                        .setGroupId(groupId)
                        .setMeetingId(p.meetingId())
                        .setDocumentId(p.documentId())
                        .setTotalQuestions(phaseItems.size())
                        .setQuizId(result.quizId()) // 페이즈별 고유 quizId
                        .setStartedAt(System.currentTimeMillis())
                        .build())
                .build();
        broadcastToRoom(groupId, startMsg);

        // 러너 생성/실행 (정답은 REVEAL 단계에서만 공개)
        QuizRunner runner = new QuizRunner(
                result.quizId(),
                groupId,
                phaseItems,
                phaseItems.size(),
                (m -> broadcastToRoom(groupId, m)),
                scheduler,
                () -> quizRunners.remove(groupId)
        );
        quizRunners.put(groupId, runner);
        runner.start();
    }

    private void stopQuiz(Long groupId, String reason) {
        var r = quizRunners.remove(groupId);
        if (r != null) r.stop(reason);
    }

    private void broadcastSystem(Long groupId, String content) {
        broadcastToRoom(groupId, ChatMessage.newBuilder()
                .setGroupId(groupId)
                .setType("SYSTEM")
                .setContent(content)
                .setTimestamp(System.currentTimeMillis())
                .build());
    }

    // ===== QUIZ_START 파라미터 파싱 (proto 또는 content에서 추출) =====
    private QuizStartParam parseQuizStart(ChatMessage msg) {
        String documentId = null;
        String meetingId = null;
        int progress = 50; // 기본 진도
        int total = 2;     // 페이즈별 기본 2문제

        try {
            if (msg.hasQuizStart()) {
                var qs = msg.getQuizStart();
                String doc = qs.getDocumentId();
                String meet = qs.getMeetingId();

                if (doc != null && !doc.isBlank()) documentId = doc.trim();
                if (meet != null && !meet.isBlank()) meetingId = meet.trim();
                if (qs.getProgressPercentage() > 0) progress = qs.getProgressPercentage();
                if (qs.getTotalQuestions() > 0)     total    = qs.getTotalQuestions();
            }
        } catch (Exception ignored) {}

        if ((documentId == null || meetingId == null) && msg.getContent() != null) {
            for (String part : msg.getContent().split("[;,&]")) {
                String[] kv = part.split("=");
                if (kv.length != 2) continue;
                switch (kv[0].trim()) {
                    case "documentId" -> documentId = kv[1].trim();
                    case "meetingId"  -> meetingId  = kv[1].trim();
                    case "progress"   -> progress   = Integer.parseInt(kv[1].trim());
                    case "total"      -> total      = Integer.parseInt(kv[1].trim());
                }
            }
        }

        if (documentId == null || documentId.isBlank()) throw new IllegalArgumentException("documentId가 없습니다.");
        if (meetingId == null || meetingId.isBlank())   throw new IllegalArgumentException("meetingId가 없습니다.");
        return new QuizStartParam(documentId, meetingId, progress, total);
    }

    private record QuizStartParam(String documentId, String meetingId, int progressPercentage, int totalQuestions) {}

    // ===== 퀴즈 러너: 문제 → 정답 공개 → 다음 문제 =====
    private static class QuizRunner {
        private static final long QUESTION_DURATION_MS = 15_000; // 풀이 시간
        private static final long REVEAL_DELAY_MS     = 800;     // 정답 공개 후 텀

        private final String quizId;
        private final long groupId;
        private final List<AiServiceClient.QuizItem> items;
        private final int total;

        private final ScheduledExecutorService scheduler;
        private final java.util.function.Consumer<com.example.bookglebookgleserver.chat.ChatMessage> broadcaster;
        private final Runnable onFinish;

        private volatile int idx = 0;
        private ScheduledFuture<?> future;

        private final ConcurrentHashMap<Long, Integer> correctCounts = new ConcurrentHashMap<>();
        private final Set<Long> answeredThisQuestion = ConcurrentHashMap.newKeySet();

        QuizRunner(String quizId,
                   long groupId,
                   List<AiServiceClient.QuizItem> items,
                   int total,
                   java.util.function.Consumer<com.example.bookglebookgleserver.chat.ChatMessage> broadcaster,
                   ScheduledExecutorService scheduler,
                   Runnable onFinish) {
            this.quizId = quizId;
            this.groupId = groupId;
            this.items = items;
            this.total = total;
            this.broadcaster = broadcaster;
            this.scheduler = scheduler;
            this.onFinish = onFinish;
        }

        void start() {
            idx = 0;
            sendCurrent();           // 문제 송출(정답 제외)
            scheduleRevealThenNext();
        }

        void handleAnswer(long userId, String quizId, int questionIndex, int selectedIndex) {
            if (!this.quizId.equals(quizId)) return; // 페이즈별 quizId로 구분
            if (questionIndex != idx) return;
            if (!answeredThisQuestion.add(userId)) return; // 중복 제출 방지

            var item = items.get(questionIndex);
            if (selectedIndex == item.correctIdx()) {
                correctCounts.merge(userId, 1, Integer::sum);
            }
        }

        private void scheduleRevealThenNext() {
            future = scheduler.schedule(() -> {
                sendReveal(); // 정답 공개

                future = scheduler.schedule(() -> {
                    answeredThisQuestion.clear();

                    int next = idx + 1;
                    if (next >= total) {
                        broadcastSummary();
                        stop("COMPLETED");
                        return;
                    }
                    idx = next;
                    sendCurrent();
                    scheduleRevealThenNext();
                }, REVEAL_DELAY_MS, TimeUnit.MILLISECONDS);

            }, QUESTION_DURATION_MS, TimeUnit.MILLISECONDS);
        }

        // 문제 전송 (정답 제외)
        private void sendCurrent() {
            var it = items.get(idx);
            ChatMessage msg = ChatMessage.newBuilder()
                    .setGroupId(groupId)
                    .setType("QUIZ_QUESTION")
                    .setTimestamp(System.currentTimeMillis())
                    .setQuizQuestion(QuizQuestion.newBuilder()
                            .setQuizId(quizId)
                            .setQuestionIndex(idx)
                            .setQuestionText(it.text())
                            .addAllOptions(it.options())
                            .setTimeoutSeconds((int)(QUESTION_DURATION_MS / 1000))
                            .setIssuedAt(System.currentTimeMillis())
                            .build())
                    .build();
            broadcaster.accept(msg);
        }

        // 정답 공개
        private void sendReveal() {
            var it = items.get(idx);
            ChatMessage reveal = ChatMessage.newBuilder()
                    .setGroupId(groupId)
                    .setType("QUIZ_REVEAL")
                    .setTimestamp(System.currentTimeMillis())
                    .setQuizQuestion(QuizQuestion.newBuilder()
                            .setQuizId(quizId)
                            .setQuestionIndex(idx)
                            .setCorrectAnswerIndex(it.correctIdx())
                            .build())
                    .build();
            broadcaster.accept(reveal);
        }

        // 최종 요약
        private void broadcastSummary() {
            List<Map.Entry<Long, Integer>> ranking = new ArrayList<>(correctCounts.entrySet());
            ranking.sort((a, b) -> {
                int c = Integer.compare(b.getValue(), a.getValue());
                return (c != 0) ? c : Long.compare(a.getKey(), b.getKey());
            });

            var sum = com.example.bookglebookgleserver.chat.QuizSummary.newBuilder()
                    .setQuizId(quizId)
                    .setTotalQuestions(total);

            int rank = 1;
            for (var e : ranking) {
                sum.addScores(com.example.bookglebookgleserver.chat.UserScore.newBuilder()
                        .setUserId(e.getKey())
                        .setNickname("") // 필요 시 채우기
                        .setCorrectCount(e.getValue())
                        .setRank(rank++)
                        .build());
            }

            ChatMessage summaryMsg = ChatMessage.newBuilder()
                    .setGroupId(groupId)
                    .setType("QUIZ_SUMMARY")
                    .setTimestamp(System.currentTimeMillis())
                    .setQuizSummary(sum.build())
                    .build();
            broadcaster.accept(summaryMsg);
        }

        void stop(String reason) {
            if (future != null && !future.isCancelled()) future.cancel(false);
            if (onFinish != null) onFinish.run();

            ChatMessage end = ChatMessage.newBuilder()
                    .setGroupId(groupId)
                    .setType("QUIZ_END")
                    .setTimestamp(System.currentTimeMillis())
                    .setQuizEnd(QuizEnd.newBuilder()
                            .setQuizId(quizId)
                            .setReason(reason)
                            .build())
                    .build();
            broadcaster.accept(end);
        }
    }

    // 방 전체 브로드캐스트
    private void broadcastToRoom(Long groupId, ChatMessage msg) {
        Set<StreamObserver<ChatMessage>> observers = roomObservers.get(groupId);
        if (observers == null || observers.isEmpty()) return;

        List<StreamObserver<ChatMessage>> toRemove = new ArrayList<>();
        for (StreamObserver<ChatMessage> observer : observers) {
            try {
                observer.onNext(msg);
            } catch (Exception e) {
                toRemove.add(observer);
            }
        }
        for (StreamObserver<ChatMessage> o : toRemove) {
            observers.remove(o);
            try { o.onCompleted(); } catch (Exception ignored) {}
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdownNow();
    }
}