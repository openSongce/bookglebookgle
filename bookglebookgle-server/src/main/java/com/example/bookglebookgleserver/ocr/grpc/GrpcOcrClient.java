package com.example.bookglebookgleserver.ocr.grpc;

import com.bgbg.ai.grpc.AIServiceGrpc;
import com.bgbg.ai.grpc.AIServiceProto.PdfInfo;
import com.bgbg.ai.grpc.AIServiceProto.ProcessPdfRequest;
import com.bgbg.ai.grpc.AIServiceProto.ProcessPdfResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Component
public class GrpcOcrClient {

    private AIServiceGrpc.AIServiceStub stub;

    @Value("${ocr.server.url}")
    private String ocrServerUrl;

    @PostConstruct
    public void init() {
        String[] parts = ocrServerUrl.split(":");
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(parts[0], Integer.parseInt(parts[1]))
                .usePlaintext()
                .build();

        this.stub = AIServiceGrpc.newStub(channel);
        log.info("✅ gRPC 클라이언트 초기화 완료 - URL: {}", ocrServerUrl);
    }

    public ProcessPdfResponse sendPdf(Long pdfId, MultipartFile file, Long meetingId) {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final ProcessPdfResponse[] responseHolder = new ProcessPdfResponse[1];

        StreamObserver<ProcessPdfRequest> requestObserver = stub.processPdf(new StreamObserver<>() {
            @Override
            public void onNext(ProcessPdfResponse response) {
                log.info("✅ OCR 결과 수신: {}", response.getMessage());
                responseHolder[0] = response;
            }

            @Override
            public void onError(Throwable t) {
                log.error("❌ gRPC 오류 발생", t);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        });

        try (InputStream inputStream = file.getInputStream()) {
            // 1. PDF Info 전송
            PdfInfo info = PdfInfo.newBuilder()
                    .setDocumentId(String.valueOf(meetingId))
                    .setFileName(file.getOriginalFilename())
                    .setMeetingId(String.valueOf(meetingId))
                    .build();
            requestObserver.onNext(ProcessPdfRequest.newBuilder().setInfo(info).build());

            // 2. Chunk 전송
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                requestObserver.onNext(ProcessPdfRequest.newBuilder()
                        .setChunk(ByteString.copyFrom(chunk)).build());
            }

            // 3. 완료 전송
            requestObserver.onCompleted();

            // 4. 응답 대기
            finishLatch.await();

        } catch (Exception e) {
            log.error("❌ 파일 전송 중 예외 발생", e);
            throw new RuntimeException(e);
        }

        return responseHolder[0];
    }

    // 📌 No OCR 처리(응답 없음) - 새로 추가
    public void sendPdfNoOcr(Long pdfId, MultipartFile file, Long meetingId) {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        final Throwable[] errorHolder = new Throwable[1];

        StreamObserver<ProcessPdfRequest> requestObserver = stub.processPdfStream(new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty response) {
                // ProcessPdfStream은 응답 데이터 없음(Empty)
            }
            @Override
            public void onError(Throwable t) {
                log.error("❌ gRPC 오류 발생 (No OCR)", t);
                errorHolder[0] = t;
                finishLatch.countDown();
            }
            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        });

        try (InputStream inputStream = file.getInputStream()) {
            // 1. PDF Info 전송
            PdfInfo info = PdfInfo.newBuilder()
                    .setDocumentId(String.valueOf(pdfId))
                    .setFileName(file.getOriginalFilename())
                    .setMeetingId(String.valueOf(meetingId))
                    .build();
            requestObserver.onNext(ProcessPdfRequest.newBuilder().setInfo(info).build());

            // 2. Chunk 전송
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                requestObserver.onNext(ProcessPdfRequest.newBuilder()
                        .setChunk(ByteString.copyFrom(chunk)).build());
            }

            // 3. 완료 전송
            requestObserver.onCompleted();

            if (errorHolder[0] != null) throw new RuntimeException(errorHolder[0]);
        } catch (Exception e) {
            log.error("❌ 파일 전송 중 예외 발생 (No OCR)", e);
            throw new RuntimeException(e);
        }
    }
}
