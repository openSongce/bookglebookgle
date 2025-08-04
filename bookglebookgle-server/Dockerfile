# 실행용 Dockerfile (bookglebookgle-server/Dockerfile)
FROM openjdk:17-jdk-slim

# 작업 디렉토리 설정
WORKDIR /app

# 시간대 설정
ENV TZ=Asia/Seoul

RUN apt-get update && \
    apt-get install -y tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    dpkg-reconfigure -f noninteractive tzdata && \
    apt-get clean

# JAR 및 환경변수 파일 복사
COPY build/libs/app.jar app.jar
COPY .env .env

# 포트 오픈
EXPOSE 8081

# JVM에 타임존 옵션, 그리고 spring.config.location 옵션 명시
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-Dspring.config.location=classpath:/application.yml,/app/config/application.yml", "-jar", "app.jar"]
