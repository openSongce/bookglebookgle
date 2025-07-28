package com.example.bookglebookgleserver.ocr.grpc;

import com.bgbg.ai.grpc.AIServiceGrpc;
import com.bgbg.ai.grpc.PdfInfo;
import com.bgbg.ai.grpc.ProcessPdfRequest;
import com.bgbg.ai.grpc.ProcessPdfResponse;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcOcrClient {

    private final String host = "localhost"; // OCR 서버 주소 (필요 시 외부 주입 가능)
    private final int port = 50051;          // OCR 서버 포트

    public ProcessPdfResponse sendPdf(Long pdfId, MultipartFile file) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        AIServiceGrpc.AIServiceStub stub = AIServiceGrpc.newStub(channel);

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

        try {
            // 1. PDF Info 전송
            PdfInfo info = PdfInfo.newBuilder()
                    .setDocumentId(String.valueOf(pdfId))
                    .setFileName(file.getOriginalFilename())
                    .build();

            requestObserver.onNext(ProcessPdfRequest.newBuilder().setInfo(info).build());

            // 2. PDF 파일을 chunk로 전송
            InputStream inputStream = file.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                requestObserver.onNext(ProcessPdfRequest.newBuilder()
                        .setChunk(ByteString.copyFrom(chunk)).build());
            }

            // 3. 완료 신호
            requestObserver.onCompleted();

            // 4. 응답 대기
            finishLatch.await(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("❌ 파일 전송 중 예외 발생", e);
            throw new RuntimeException(e);
        } finally {
            channel.shutdown();
        }

        return responseHolder[0];
    }
}
