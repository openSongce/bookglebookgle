# 실행용 Dockerfile (bookglebookgle-server/Dockerfile)
FROM openjdk:17-jdk-slim

# 1. 타임존 환경변수 설정
ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul"

# 2. 시스템 타임존도 설정 (선택적 – 보조 설정)
RUN ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

WORKDIR /app
COPY build/libs/app.jar app.jar
COPY .env .env

EXPOSE 8081

# 3. ENTRYPOINT는 그대로
ENTRYPOINT ["java", "-jar", "app.jar"]
