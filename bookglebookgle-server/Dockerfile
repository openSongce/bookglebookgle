# 1. Temurin JRE 17 사용
FROM eclipse-temurin:17-jre

# 2. 작업 디렉터리 설정
WORKDIR /app

# 3. Gradle이 생성한 단 하나의 fat-jar만 복사
#    bootJar가 만드는 *-boot.jar 패턴을 사용합니다.
COPY build/libs/*-boot.jar app.jar

# 4. 컨테이너 기동 커맨드
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
