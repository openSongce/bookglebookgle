# bookglebookgle-server/Dockerfile

FROM eclipse-temurin:17-jre
WORKDIR /app

# Gradle이 생성한 app.jar 한 파일만 복사
COPY build/libs/app.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
