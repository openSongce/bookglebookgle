package com.example.bookglebookgleserver;

import com.example.bookglebookgleserver.redis.RedisTestService;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

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

    @Bean
    public CommandLineRunner testRedis(RedisTestService redisTestService) {
        return args -> {
            redisTestService.testRedis();
        };
    }

}
