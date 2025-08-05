package com.example.bookglebookgleserver;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SpringBootApplication
public class BookglebookgleServerApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .filename(".env")
                .load();

        System.setProperty("DB_USERNAME", dotenv.get("DB_USERNAME"));
        System.setProperty("DB_PASSWORD", dotenv.get("DB_PASSWORD"));

        System.out.println("MAIL_USERNAME: " + System.getenv("MAIL_USERNAME"));


        SpringApplication.run(BookglebookgleServerApplication.class, args);

    }
    
    @Component
    public class GrpcServerLauncher {

        private static final Logger log = LoggerFactory.getLogger(GrpcServerLauncher.class);

        @PostConstruct
        public void startGrpcServer() {
            Thread grpcThread = new Thread(() -> {
                try {
                    log.info("gRPC 서버 초기화 중...");
                    com.example.bookglebookgleserver.grpc.PdfSyncGrpcServer.main(null);
                } catch (Exception e) {
                    log.error("gRPC 서버 실행 중 예외 발생", e);
                }
            });

            grpcThread.setDaemon(false);
            grpcThread.start();
        }
    }


}
