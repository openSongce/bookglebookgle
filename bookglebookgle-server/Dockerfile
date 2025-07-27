FROM openjdk:17-jdk-slim
WORKDIR /app

ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul"

RUN apt-get update && apt-get install -y tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    dpkg-reconfigure -f noninteractive tzdata

COPY build/libs/app.jar app.jar
COPY .env .env

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
