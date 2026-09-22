FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 spring
COPY --from=builder /workspace/build/libs/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]