package com.example.bookglebookgleserver.chat.grpc;

import com.bgbg.ai.grpc.AIServiceGrpc;
import com.bgbg.ai.grpc.AIServiceProto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AiServiceClient {

    private AIServiceGrpc.AIServiceBlockingStub blockingStub;
//    private AIServiceGrpc.AIServiceStub stub;  // ⭐️ 추가 (비동기용)
    private AIServiceGrpc.AIServiceStub asyncStub;

    @Value("${ocr.server.url}")
    private String ocrServerUrl; // 기존 OCR 환경 변수

    private ManagedChannel channel;


    // [1] 그룹별 토론 세션ID 관리
    private final Map<Long, String> groupSessionMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            String[] parts = ocrServerUrl.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid ocr.server.url format. Expected host:port but got " + ocrServerUrl);
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()
                    .build();

            blockingStub = AIServiceGrpc.newBlockingStub(channel);
            asyncStub = AIServiceGrpc.newStub(channel);

            log.info("[AI gRPC] 클라이언트 초기화 완료 - host={}, port={}", host, port);
        } catch (Exception e) {
            log.error(" [AI gRPC] 초기화 실패", e);
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info(" [AI gRPC] 채널 종료");
        }
    }


    // [2] 토론 세션 시작
    public DiscussionInitResponse initializeDiscussion(Long groupId) {
        String sessionId = UUID.randomUUID().toString();
        groupSessionMap.put(groupId, sessionId);

        DiscussionInitRequest request = DiscussionInitRequest.newBuilder()
                .setDocumentId("doc-" + groupId)
                .setMeetingId(String.valueOf(groupId))
                .setSessionId(sessionId)
                .setStartedAt(System.currentTimeMillis() / 1000)
                .build();

        log.info("[AI gRPC] 토론 시작 요청: {}", request);
        DiscussionInitResponse response = blockingStub.initializeDiscussion(request);
        log.info("[AI gRPC] 토론 시작 응답: {}", response);

        return response;
    }

    // [3] 토론 세션 종료
    public DiscussionEndResponse endDiscussion(Long groupId) {
        String sessionId = groupSessionMap.getOrDefault(groupId, "session-" + groupId);

        DiscussionEndRequest request = DiscussionEndRequest.newBuilder()
                .setMeetingId(String.valueOf(groupId))
                .setSessionId(sessionId)
                .setEndedAt(System.currentTimeMillis() / 1000)
                .build();

        log.info("[AI gRPC] 토론 종료 요청: {}", request);
        DiscussionEndResponse response = blockingStub.endDiscussion(request);
        log.info("[AI gRPC] 토론 종료 응답: {}", response);

        groupSessionMap.remove(groupId);
        return response;
    }

    // [4] 토론 Moderation 메시지 스트림 (★ 실제 AI 피드백 요청!)
    public void streamChatModeration(
            Long groupId, Long senderId, String senderName, String content,
            StreamObserver<ChatMessageResponse> responseObserver // 응답 스트림 처리 (콜백으로)
    ) {
        String sessionId = groupSessionMap.getOrDefault(groupId, "session-" + groupId);

        // gRPC 비동기 스트림 생성
        StreamObserver<ChatMessageRequest> requestObserver = asyncStub.processChatMessage(responseObserver);

        // 실제 메시지 전송
        ChatMessageRequest req = ChatMessageRequest.newBuilder()
                .setDiscussionSessionId(sessionId)
                .setMessage(content)
                .setTimestamp(System.currentTimeMillis())
                .setSender(
                        User.newBuilder()
                                .setUserId(String.valueOf(senderId))
                                .setNickname(senderName)
                                .build()
                )
                .setUseChatContext(true)
                .setStoreInHistory(true)
                .build();

        try {
            requestObserver.onNext(req); // 메시지 전송
            requestObserver.onCompleted(); // 단일 메시지(스트림 끝)
            log.info("[AI gRPC] Moderation 요청 송신 완료: {}", req);
        } catch (Exception e) {
            log.error("[AI gRPC] Moderation 요청 중 예외", e);
            requestObserver.onError(e);
        }
    }



    // 퀴즈 세트 요청 (문제+정답 포함)
    public GenerateQuizResult generateQuiz(String documentId, String meetingId, int progressPercentage) {
        QuizRequest req = QuizRequest.newBuilder()
                .setDocumentId(documentId)
                .setMeetingId(meetingId)
                .setProgressPercentage(progressPercentage) // 50 or 100
                .build();

        // GenerateQuiz 타임아웃
        var res = blockingStub.withDeadlineAfter(8, TimeUnit.SECONDS).generateQuiz(req);
        if (!res.getSuccess()) {
            throw new IllegalStateException("AI GenerateQuiz failed: " + res.getMessage());
        }

        List<QuizItem> items = new ArrayList<>();
        for (Question q : res.getQuestionsList()) {
            items.add(new QuizItem(q.getQuestionText(), q.getOptionsList(), q.getCorrectAnswerIndex()));
        }
        return new GenerateQuizResult(res.getQuizId(), items);
    }

    // 단순 DTO
    public record QuizItem(String text, List<String> options, int correctIdx) {}
    public record GenerateQuizResult(String quizId, List<QuizItem> items) {}



}
