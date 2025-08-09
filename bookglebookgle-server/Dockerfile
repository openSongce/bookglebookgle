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

# JAR 파일 복사
COPY build/libs/app.jar app.jar

# 포트 오픈
EXPOSE 8081

# ENTRYPOINT에서 .env 파일을 직접 참조하도록 변경
ENTRYPOINT ["java", "-jar", "app.jar"]
