package com.example.bookglebookgleserver.grpc;

import com.example.bookglebookgleserver.pdf.grpc.PdfSyncServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class PdfSyncGrpcServer {
    public static void main(String[] args) {
        try {
            Server server = ServerBuilder.forPort(6565)
                .addService(new PdfSyncServiceImpl())
                .build();

            System.out.println("gRPC 서버 시작: 포트 6565");
            server.start();
            server.awaitTermination();
        } catch (Exception e) {
            System.err.println("gRPC 서버 실행 중 예외 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
