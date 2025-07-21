# 단계 1: JDK 17 런타임 이미지 사용
FROM eclipse-temurin:17-jre

# 단계 2: 작업 디렉터리 설정
WORKDIR /app

# 단계 3: 빌드된 JAR 복사
# (build/libs 안에 있는 fat-jar 파일명을 맞춰 주세요)
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 단계 4: 컨테이너 기동 시 실행할 커맨드
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
