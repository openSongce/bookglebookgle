package com.example.bookglebookgleserver.grpc;

import com.example.bookglebookgleserver.pdf.grpc.PdfSyncServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfSyncGrpcServer {

    private static final Logger log = LoggerFactory.getLogger(PdfSyncGrpcServer.class);

    public static void main(String[] args) {
        try {
            Server server = ServerBuilder.forPort(6565)
                    .addService(new PdfSyncServiceImpl())
                    .build();

            log.info("✅ gRPC 서버 시작: 포트 6565");

            server.start();
            server.awaitTermination();

            log.info("⏸️ gRPC 서버: awaitTermination 호출됨 (정상 대기 중)");
        } catch (Exception e) {
            log.error("❌ gRPC 서버 실행 중 예외 발생", e);
        }
    }
}
