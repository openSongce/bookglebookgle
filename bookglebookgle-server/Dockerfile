# bookglebookgle-server/Dockerfile

# 1. 경량화된 Temurin JRE 17 베이스 이미지 사용
FROM eclipse-temurin:17-jre

# 2. 애플리케이션 작업 디렉터리
WORKDIR /app

# 3. Jenkins(또는 로컬) 빌드가 생성한 app.jar 복사
COPY build/libs/app.jar app.jar

# 4. 외부에서 매핑할 포트
EXPOSE 8081

# 5. 컨테이너 기동 시 JAR 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
