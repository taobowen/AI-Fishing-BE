# syntax=docker/dockerfile:1
# Fargate worker/API tasks are X86_64. Pin linux/amd64 so Apple Silicon local builds are pullable.
FROM --platform=linux/amd64 maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

FROM --platform=linux/amd64 eclipse-temurin:21-jre
RUN useradd --system --create-home --home-dir /app app
WORKDIR /app
COPY --from=build /src/target/ai-fishing-be-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["java", "-jar", "app.jar"]
