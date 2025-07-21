# 1) 경량 JRE 17 베이스
FROM eclipse-temurin:17-jre

# 2) 작업 디렉터리
WORKDIR /app

# 3) Gradle이 만들어준 app.jar 하나만 복사
COPY build/libs/app.jar app.jar

# 4) 컨테이너 기동 커맨드
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
