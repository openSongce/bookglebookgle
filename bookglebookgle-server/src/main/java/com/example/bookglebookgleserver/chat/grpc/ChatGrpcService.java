package com.example.bookglebookgleserver.chat.grpc;

import com.example.bookglebookgleserver.chat.*;
import com.bgbg.ai.grpc.AIServiceProto.ChatMessageResponse; // (AI 응답용 proto 메시지 import)
import com.bgbg.ai.grpc.AIServiceProto.DiscussionInitResponse; // ★ 추가: 토론 시작 응답
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final AiServiceClient aiServiceClient;

    // 채팅방별로 클라이언트 목록 관리
    private final ConcurrentHashMap<Long, Set<StreamObserver<ChatMessage>>> roomObservers = new ConcurrentHashMap<>();
    // 토론 활성 상태 관리
    private final ConcurrentHashMap<Long, Boolean> discussionActiveMap = new ConcurrentHashMap<>();

    // ✅ 퀴즈 상태/스케줄 관리
    private final ConcurrentHashMap<Long, QuizRunner> quizRunners = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);


    // 필드 추가
    private final ConcurrentHashMap<Long, String> userNickCache = new ConcurrentHashMap<>();




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
                        log.info("[gRPC-Chat] 그룹 {}에 클라이언트 연결! 현재 접속 수: {}", groupId, roomObservers.get(groupId).size());
                    }

                    String msgType = message.getType();

                    if ("QUIZ_START".equals(msgType)) {
                        try {
                            QuizStartParam p = parseQuizStart(message);

                            // groupId(채팅방)와 meetingId 정합성 체크: 다르면 거부
                            if (!String.valueOf(groupId).equals(p.meetingId())) {
                                log.warn("[QUIZ_START] meetingId != groupId (meetingId={}, groupId={})", p.meetingId(), groupId);
                                broadcastSystem(groupId, "퀴즈 시작 실패: meetingId가 방 ID와 다릅니다.");
                                return;
                            }

                            startQuiz(groupId, message.getSenderId(), p);
//                            ChatGrpcService.this.broadcastToRoom(groupId, message);
                        } catch (IllegalArgumentException ex) {
                            log.warn("[QUIZ_START] 잘못된 요청: {}", ex.getMessage());
                            broadcastSystem(groupId, "퀴즈 시작 실패: " + ex.getMessage());
                        } catch (Exception ex) {
                            log.error("[QUIZ_START] 처리 중 오류", ex);
                            broadcastSystem(groupId, "퀴즈 시작 실패: 서버 오류");
                        }
                        return;
                    }

                    // ==== ✅ 퀴즈 강제 종료 ====
                    if ("QUIZ_CANCEL".equals(msgType)) {
                        stopQuiz(groupId, "CANCELLED");
                        ChatGrpcService.this.broadcastToRoom(groupId, message);
                        return;
                    }

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
                        ChatGrpcService.this.broadcastToRoom(groupId, message);
                        return; // 다른 처리 안 함
                    }

                    // === 1. 토론 시그널 분기 및 ai_service gRPC 호출 ===
                    if ("DISCUSSION_START".equals(msgType)) {
                        log.info("[gRPC-Chat] 토론 시작 시그널 수신 - groupId={}", groupId);
                        discussionActiveMap.put(groupId, true); // 토론 활성화
                        try {
                            // ★ AI 서버에 토론 시작 요청 후 응답 수신
                            DiscussionInitResponse resp = aiServiceClient.initializeDiscussion(groupId);

                            if (!resp.getSuccess()) {
                                // 실패 시 상태 롤백 및 안내
                                discussionActiveMap.remove(groupId);
                                broadcastSystem(groupId, "토론 시작 실패: " + resp.getMessage());
                                return;
                            }


                            // 2) ★ 토론 토픽/추천 토픽 방송
                            ChatMessage topicsMsg = ChatMessage.newBuilder()
                                    .setGroupId(groupId)
                                    .setSenderId(0L)
                                    .setSenderName("AI")
                                    .setTimestamp(System.currentTimeMillis())
                                    .setType("AI_RESPONSE") // 프론트와 합의된 타입
                                    .setAiResponse(resp.getRecommendedTopic()) // 추천 토픽(없으면 빈 문자열)
                                    .addAllSuggestedTopics(resp.getDiscussionTopicsList()) // 핵심: 토픽 목록
                                    .build();

                            ChatGrpcService.this.broadcastToRoom(groupId, topicsMsg);

                        } catch (Exception e) {
                            log.error("[gRPC-Chat] AI 토론 시작 요청 실패", e);
                            discussionActiveMap.remove(groupId);
                            broadcastSystem(groupId, "토론 시작 실패: 서버 오류");
                        }
                        return;
                    } else if ("DISCUSSION_END".equals(msgType)) {
                        log.info("[gRPC-Chat] 토론 종료 시그널 수신 - groupId={}", groupId);
                        discussionActiveMap.remove(groupId); // 토론 비활성화
                        try {
                            aiServiceClient.endDiscussion(groupId);
                        } catch (Exception e) {
                            log.error("[gRPC-Chat] AI 토론 종료 요청 실패", e);
                        }
                        ChatGrpcService.this.broadcastToRoom(groupId, message);
                        return;
                    }
                    // === 2. 토론 중 AI Moderation (피드백/알림) 요청 ===
                    else if (isDiscussionActive(groupId)) {
                        ChatGrpcService.this.broadcastToRoom(groupId, message);
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
                                        ChatGrpcService.this.broadcastToRoom(groupId, aiMsg);
                                    }
                                    @Override public void onError(Throwable t) { log.error("AI 응답 오류", t); }
                                    @Override public void onCompleted() { }
                                }
                        );
                        return;
                    }
                    // === 3. 일반 채팅 메시지 처리 ===
                    else {
                        saveAndBroadcastNormalMessage(message, groupId);

                        return;
                    }

                } catch (Exception e) {
                    log.error("[gRPC-Chat] 처리 중 예외 발생", e);
                }
            }

            private void saveAndBroadcastNormalMessage(ChatMessage message, Long groupId) {
                // 채팅방, 유저 조회 및 예외처리
                ChatRoom chatRoom = chatRoomRepository.findById(groupId).orElse(null);
                if (chatRoom == null) {
                    log.warn("[gRPC-Chat] groupId={} ChatRoom 없음! 메시지 저장/전송 생략: {}", groupId, message.getContent());
                    return;
                }
                User sender = userRepository.findById(message.getSenderId()).orElse(null);
                if (sender == null) {
                    log.warn("[gRPC-Chat] senderId={} User 없음! 메시지 저장/전송 생략", message.getSenderId());
                    return;
                }
                userNickCache.put(sender.getId(), sender.getNickname());

                // DB 저장
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
                    log.info("[gRPC-Chat] 메시지 저장: roomId={}, sender={}, content={}", groupId, sender.getId(), message.getContent());
                } catch (Exception ex) {
                    log.error("[gRPC-Chat] 메시지 저장 에러: {}", ex.getMessage(), ex);
                }
                // 브로드캐스트
                ChatGrpcService.this.broadcastToRoom(groupId, message);
            }

            @Override
            public void onError(Throwable t) {
                log.error("[gRPC-Chat] 클라이언트 채널 오류 발생: {}", t.getMessage(), t);
                removeObserver();
            }

            @Override
            public void onCompleted() {
                log.info("[gRPC-Chat] 클라이언트 채널 정상 종료");
                removeObserver();
                responseObserver.onCompleted();
            }

            private void removeObserver() {
                if (groupId != null) {
                    Set<StreamObserver<ChatMessage>> observers = roomObservers.get(groupId);
                    if (observers != null) {
                        observers.remove(responseObserver);
                        log.info("[gRPC-Chat] 그룹 {}에서 클라이언트 연결 해제! 남은 접속 수: {}", groupId, observers.size());
                    }
                }
            }

            private boolean isDiscussionActive(Long groupId) {
                return discussionActiveMap.getOrDefault(groupId, false);
            }
        };
    }

    // === 퀴즈 시작 ===
    private void startQuiz(Long groupId, Long senderId, QuizStartParam p) {
        // TODO: 방장 권한 체크
        if (quizRunners.containsKey(groupId)) {
            broadcastSystem(groupId, "이미 진행 중인 퀴즈가 있습니다.");
            return;
        }

        log.info("[QUIZ_START] groupId={}, meetingId={}, documentId={}, total={}",
                groupId, p.meetingId(), p.documentId(), p.totalQuestions());

        if (p.documentId() == null || p.documentId().isBlank() ||
                p.meetingId()  == null || p.meetingId().isBlank()) {
            throw new IllegalArgumentException("documentId와 meetingId는 필수입니다.");
        }

        // (이중 체크) 방 ID와 meetingId 일치 보장
        if (!String.valueOf(groupId).equals(p.meetingId())) {
            throw new IllegalArgumentException("meetingId가 방 ID와 일치하지 않습니다.");
        }

        // AI에서 문제 세트 수신
        var result = aiServiceClient.generateQuiz(
                groupId
                , p.progressPercentage());
        var items = result.items();
        int total = Math.min(p.totalQuestions(), items.size());
        if (total <= 0) {
            broadcastSystem(groupId, "퀴즈 문제를 불러오지 못했습니다.");
            return;
        }


        //  서버가 풍부한 QUIZ_START 메시지(진도율 포함) 브로드캐스트
        ChatMessage startMsg = ChatMessage.newBuilder()
                .setGroupId(groupId)
                .setType("QUIZ_START")
                .setTimestamp(System.currentTimeMillis())
                .setQuizStart(com.example.bookglebookgleserver.chat.QuizStart.newBuilder()
                        .setGroupId(groupId)
                        .setMeetingId(p.meetingId())
                        .setDocumentId(p.documentId())
                        .setTotalQuestions(total)
                        .setQuizId(result.quizId())
                        .setStartedAt(System.currentTimeMillis())
                        .build())
                .build();
        broadcastToRoom(groupId, startMsg);

        //  Runner에 phase/진도율도 보관
        QuizRunner runner = new QuizRunner(
                result.quizId(), groupId, items, total,
                (java.util.function.Consumer<com.example.bookglebookgleserver.chat.ChatMessage>)
                        (m -> ChatGrpcService.this.broadcastToRoom(groupId, m)),
                scheduler,
                () -> quizRunners.remove(groupId),
                this.userRepository,
                this.userNickCache
        );
        quizRunners.put(groupId, runner);

        // 1번 문제(+정답) 즉시 송출, 이후 15초 간격
        runner.start();
    }

    // === 퀴즈 종료/정리 ===
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

    // === 간단 파서(프론트가 별도 payload 안 보낸다고 가정 시) ===
    private QuizStartParam parseQuizStart(ChatMessage msg) {
        // 실제로는 msg에 QuizStart payload를 넣는 게 더 좋음.
        // 여기서는 content 파싱 예시만.
        // ex) "documentId=doc_xxx;meetingId=meet_xxx;progress=50;total=4"
        String documentId = null;
        String meetingId = null;
        int progress = 50;
        int total = 4;

        // 1) proto payload 우선
        try {
            if (msg.hasQuizStart()) {
                var qs = msg.getQuizStart();
                String doc = qs.getDocumentId(); // 빈 문자열일 수 있음(proto3 기본)
                String meet = qs.getMeetingId();

                if (doc != null && !doc.isBlank()) {
                    documentId = doc.trim();
                }
                if (meet != null && !meet.isBlank()) {
                    meetingId = meet.trim();
                }
                if (qs.getProgressPercentage() > 0) progress = qs.getProgressPercentage();
                if (qs.getTotalQuestions() > 0)     total    = qs.getTotalQuestions();
            }
        } catch (Exception ignored) {}

        // 2) content key=value fallback
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

        // 3) 필수값 최종 검증: 하나라도 없으면 예외
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId가 없습니다.");
        }
        if (meetingId == null || meetingId.isBlank()) {
            throw new IllegalArgumentException("meetingId가 없습니다.");
        }

        return new QuizStartParam(documentId, meetingId, progress, total);
    }
    private record QuizStartParam(String documentId, String meetingId, int progressPercentage, int totalQuestions) {}


    private static class QuizRunner {
        private static final long QUESTION_DURATION_MS = 15_000; // 문제 풀이 시간
        private static final long REVEAL_DELAY_MS     = 800;     // 정답 공개 후 다음 문제까지 텀

        private final String quizId;
        private final long groupId;
        private final List<AiServiceClient.QuizItem> items;
        private final int total;

        private final ScheduledExecutorService scheduler;
        private final java.util.function.Consumer<com.example.bookglebookgleserver.chat.ChatMessage> broadcaster;
        private final Runnable onFinish;

        private volatile int idx = 0;                         // 현재 문제 index
        private ScheduledFuture<?> future;                    // 현재 예약 작업

        // 정답 집계: userId -> 맞춘 개수
        private final ConcurrentHashMap<Long, Integer> correctCounts = new ConcurrentHashMap<>();
        // 현재 문제에서 중복 제출 방지
        private final Set<Long> answeredThisQuestion = ConcurrentHashMap.newKeySet();
        // 현재 문제에서 유저별 선택 기록: userId -> selectedIndex
        private final Map<Long, Integer> answersThisQuestion = new ConcurrentHashMap<>();


        private final UserRepository userRepository;
        private final ConcurrentHashMap<Long, String> userNickCache;

        // QuizRunner 필드
        private final Set<Long> participants = ConcurrentHashMap.newKeySet();



        QuizRunner(String quizId,
                   long groupId,
                   List<AiServiceClient.QuizItem> items,
                   int total,
                   java.util.function.Consumer<com.example.bookglebookgleserver.chat.ChatMessage> broadcaster,
                   ScheduledExecutorService scheduler,
                   Runnable onFinish,
        UserRepository userRepository,
            ConcurrentHashMap<Long, String> userNickCache) {
            this.quizId = quizId;
            this.groupId = groupId;
            this.items = items;
            this.total = total;
            this.broadcaster = broadcaster;
            this.scheduler = scheduler;
            this.onFinish = onFinish;
            this.userRepository = userRepository;
            this.userNickCache = userNickCache;
        }

        void start() {
            // 1번 문제 송출
            idx = 0;
            sendCurrent();
            // 정답 공개 예약
            scheduleRevealThenNext();
        }

        // 클라가 제출한 답 처리
        void handleAnswer(long userId, String quizId, int questionIndex, int selectedIndex) {
            if (!this.quizId.equals(quizId)) return;
            if (questionIndex != idx) return;
            if (!answeredThisQuestion.add(userId)) return;

            participants.add(userId); // ★ 참여자 기록

            answersThisQuestion.put(userId, selectedIndex);
            var item = items.get(questionIndex);
            int delta = (selectedIndex == item.correctIdx()) ? 1 : 0;
            correctCounts.merge(userId, delta, Integer::sum);
        }

        private void scheduleRevealThenNext() {
            // 1) QUESTION_DURATION_MS 뒤에 정답 공개
            future = scheduler.schedule(() -> {
                sendReveal();

                // 2) REVEAL_DELAY_MS 후 다음 문제로
                future = scheduler.schedule(() -> {
                    // 다음 문제로 넘어가기 전에, 현재 문제에 대한 중복 제출 기록 초기화
                    answeredThisQuestion.clear();
                    answersThisQuestion.clear();

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

        // 문제 송출 (정답 제외)
        private void sendCurrent() {
            var it = items.get(idx);
            log.info("[QUIZ] Q{} 송출: {} 옵션, 정답은 숨김", idx + 1, it.options().size());

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
                            // 정답은 여기서 보내지 않음
                            .build())
                    .build();
            broadcaster.accept(msg);
        }

        // 정답 공개 (proto 수정 없이 QuizQuestion 재사용)
        private void sendReveal() {
            var it = items.get(idx);
            int correctAnswer = it.correctIdx();

            // 추가 검증
            if (correctAnswer < 0 || correctAnswer >= it.options().size()) {
                log.error("[QUIZ] 잘못된 정답 인덱스: {}, 선택지 수: {}", correctAnswer, it.options().size());
                return;
            }

            log.info("[QUIZ] Q{} 정답 공개: {}번 - {}", idx + 1, correctAnswer, it.options().get(correctAnswer));

            // PerUserAnswer 리스트 구성
            QuizReveal.Builder revealBuilder = QuizReveal.newBuilder()
                    .setQuizId(quizId)
                    .setQuestionIndex(idx)
                    .setCorrectAnswerIndex(correctAnswer);

            for (Map.Entry<Long, Integer> e : answersThisQuestion.entrySet()) {
                long userId = e.getKey();
                int selectedIndex = e.getValue();
                boolean isCorrect = (selectedIndex == correctAnswer);

                revealBuilder.addUserAnswers(
                        PerUserAnswer.newBuilder()
                                .setUserId(userId)
                                .setSelectedIndex(selectedIndex)
                                .setIsCorrect(isCorrect)
                                .build()
                );
            }
            ChatMessage revealMsg = ChatMessage.newBuilder()
                    .setGroupId(groupId)
                    .setType("QUIZ_REVEAL")
                    .setTimestamp(System.currentTimeMillis())
                    .setQuizReveal(revealBuilder.build())   // ✅ oneof: quiz_reveal
                    .build();

//            ChatMessage reveal = ChatMessage.newBuilder()
//                    .setGroupId(groupId)
//                    .setType("QUIZ_REVEAL")
//                    .setTimestamp(System.currentTimeMillis())
//                    .setContent("정답: " + correctAnswer) // content에도 정답 추가
//                    .setQuizQuestion(QuizQuestion.newBuilder()
//                            .setQuizId(quizId)
//                            .setQuestionIndex(idx)
//                            .setQuestionText(it.text())
//                            .addAllOptions(it.options())
//                            .setCorrectAnswerIndex(correctAnswer)
//                            .build())
//                    .build();

            // 전송 전 최종 확인
            log.info("[QUIZ] 전송할 정답: {}", revealMsg.getQuizReveal().getCorrectAnswerIndex());
            broadcaster.accept(revealMsg);
        }

        private void broadcastSummary() {
            // 참여자 전원의 점수로 리스트 구성 (0점도 포함)
            List<Map.Entry<Long, Integer>> base = new ArrayList<>();
            for (Long uid : participants) {
                base.add(Map.entry(uid, correctCounts.getOrDefault(uid, 0)));
            }

            // 정렬: 점수 desc, userId asc
            base.sort((a, b) -> {
                int c = Integer.compare(b.getValue(), a.getValue());
                if (c != 0) return c;
                return Long.compare(a.getKey(), b.getKey());
            });

            var sum = QuizSummary.newBuilder()
                    .setQuizId(quizId)
                    .setTotalQuestions(total);

            // 닉네임 캐시 보강
            Set<Long> missing = new java.util.HashSet<>();
            for (var e : base) if (!userNickCache.containsKey(e.getKey())) missing.add(e.getKey());
            if (!missing.isEmpty()) {
                userRepository.findAllById(missing)
                        .forEach(u -> userNickCache.put(u.getId(), u.getNickname()));
            }

            // 순위 매기기 (동점은 동일 순위 부여)
            int rank = 0;
            int processed = 0;
            Integer prevScore = null;

            for (var e : base) {
                processed++;
                int score = e.getValue();
                if (prevScore == null || score < prevScore) {
                    rank = processed;       // 다음 순위 = 현재까지 처리한 인원 수
                    prevScore = score;
                }
                long uid = e.getKey();
                sum.addScores(UserScore.newBuilder()
                        .setUserId(uid)
                        .setNickname(userNickCache.getOrDefault(uid, "익명"))
                        .setCorrectCount(score)
                        .setRank(rank)
                        .build());
            }


            broadcaster.accept(ChatMessage.newBuilder()
                    .setGroupId(groupId)
                    .setType("QUIZ_SUMMARY")
                    .setTimestamp(System.currentTimeMillis())
                    .setQuizSummary(sum.build())
                    .build());
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


//    private static class QuizRunner {
//        private final String quizId;
//        private final long groupId;
//        private final List<AiServiceClient.QuizItem> items;
//        private final int total;
//
//
//        private final ScheduledExecutorService scheduler;
//        // import 충돌 방지: 필드 제네릭을 FQCN으로
//        private final java.util.function.Consumer<com.example.bookglebookgleserver.chat.ChatMessage> broadcaster;
//        private final Runnable onFinish;
//
//        private volatile int idx = 0;                 // 현재 문제 index
//        private ScheduledFuture<?> future;
//
//        // 정답 집계: userId -> 맞춘 개수
//        private final ConcurrentHashMap<Long, Integer> correctCounts = new ConcurrentHashMap<>();
//        //  현재 문제에서 중복 제출 방지
//        private final Set<Long> answeredThisQuestion = ConcurrentHashMap.newKeySet();
//
//        QuizRunner(String quizId,
//                   long groupId,
//                   List<AiServiceClient.QuizItem> items,
//                   int total,
//                   java.util.function.Consumer<com.example.bookglebookgleserver.chat.ChatMessage> broadcaster,
//                   ScheduledExecutorService scheduler,
//                   Runnable onFinish) {
//            this.quizId = quizId;
//            this.groupId = groupId;
//            this.items = items;
//            this.total = total;
//            this.broadcaster = broadcaster;
//            this.scheduler = scheduler;
//            this.onFinish = onFinish;
//        }
//
//        void start() {
//            // 1번 문제(+정답) 즉시
//            sendCurrent();
//
//            // 이후 15초마다 다음 문제(+정답)
//            future = scheduler.scheduleAtFixedRate(() -> {
//                // 다음 문제로 넘어가기 전에, 현재 문제에 대한 중복 제출 기록 초기화
//                answeredThisQuestion.clear();
//
//                idx++;
//                if (idx >= total) {
//                    // 끝: 요약 먼저, 그 다음 종료 알림
//                    broadcastSummary();
//                    stop("COMPLETED");
//                    return;
//                }
//                sendCurrent();
//            }, 15, 15, TimeUnit.SECONDS);
//        }
//
//        // 정답 제출 처리
//        void handleAnswer(long userId, String quizId, int questionIndex, int selectedIndex) {
//            if (!this.quizId.equals(quizId)) return;
//            if (questionIndex != idx) return;           // 현재 문제에 대해서만 인정
//            if (!answeredThisQuestion.add(userId)) return; // 이미 제출했다면 무시
//
//            var item = items.get(questionIndex);
//            if (selectedIndex == item.correctIdx()) {
//                correctCounts.merge(userId, 1, Integer::sum);
//            }
//        }
//
//        //최종 요약 브로드캐스트
//        private void broadcastSummary() {
//            // 정답수 desc, userId asc 정렬
//            List<Map.Entry<Long, Integer>> ranking = new ArrayList<>(correctCounts.entrySet());
//            ranking.sort((a, b) -> {
//                int c = Integer.compare(b.getValue(), a.getValue());
//                if (c != 0) return c;
//                return Long.compare(a.getKey(), b.getKey());
//            });
//
//            var sum = com.example.bookglebookgleserver.chat.QuizSummary.newBuilder()
//                    .setQuizId(quizId)
//                    .setTotalQuestions(total);  // 필요시 phase 등 추가 가능
//
//            int rank = 1;
//            for (var e : ranking) {
//                sum.addScores(com.example.bookglebookgleserver.chat.UserScore.newBuilder()
//                        .setUserId(e.getKey())
//                        .setNickname("") // 필요 시 닉네임 채우기
//                        .setCorrectCount(e.getValue())
//                        .setRank(rank++)
//                        .build());
//            }
//
//            ChatMessage summaryMsg = ChatMessage.newBuilder()
//                    .setGroupId(groupId)
//                    .setType("QUIZ_SUMMARY")
//                    .setTimestamp(System.currentTimeMillis())
//                    .setQuizSummary(sum.build())
//                    .build();
//            broadcaster.accept(summaryMsg);
//        }
//
//        void stop(String reason) {
//            if (future != null && !future.isCancelled()) future.cancel(false);
//
//            if (onFinish != null) onFinish.run();
//
//            ChatMessage end = ChatMessage.newBuilder()
//                    .setGroupId(groupId)
//                    .setType("QUIZ_END")
//                    .setTimestamp(System.currentTimeMillis())
//                    .setQuizEnd(QuizEnd.newBuilder()
//                            .setQuizId(quizId)
//                            .setReason(reason)
//                            .build())
//                    .build();
//            broadcaster.accept(end);
//        }
//
//        private void sendCurrent() {
//            var it = items.get(idx);
//            log.info("[QUIZ] Q{} 송출: options={}, correct(hidden)", idx, it.options().size());
//            ChatMessage msg = ChatMessage.newBuilder()
//                    .setGroupId(groupId)
//                    .setType("QUIZ_QUESTION") // 문제+정답 같이 담아 보냄
//                    .setTimestamp(System.currentTimeMillis())
//                    .setQuizQuestion(QuizQuestion.newBuilder()
//                            .setQuizId(quizId)
//                            .setQuestionIndex(idx)
//                            .setQuestionText(it.text())
//                            .addAllOptions(it.options())
//                            .setTimeoutSeconds(15)
//                            .setIssuedAt(System.currentTimeMillis())
////                            .setCorrectAnswerIndex(it.correctIdx())  // 정답 포함
//                            .build())
//                    .build();
//            broadcaster.accept(msg);
//        }
//    }




    // ChatGrpcService 클래스 바깥 메서드 (외부에서 사용)
    private void broadcastToRoom(Long groupId, ChatMessage msg) {
        Set<StreamObserver<ChatMessage>> observers = roomObservers.get(groupId);
        if (observers == null || observers.isEmpty()) return;

        // 예외 난 스트림 정리
        List<StreamObserver<ChatMessage>> toRemove = new ArrayList<>();

        for (StreamObserver<ChatMessage> observer : observers) {
            try {
                observer.onNext(msg);
            } catch (Exception e) {
                toRemove.add(observer);
            }
        }

        // 끊긴 스트림 제거
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
