# Stage 1: Build
# Run: docker build -f ai-agent-swagger/Dockerfile -t ai-agent-swagger .
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy poms first to cache dependencies
COPY pom.xml ./pom.xml
COPY ai-agent-swagger/pom.xml ./ai-agent-swagger/pom.xml

RUN mvn dependency:go-offline -pl ai-agent-swagger -B --no-transfer-progress || true

# Copy sources and build
COPY ai-agent-swagger/src ./ai-agent-swagger/src

RUN mvn package -pl ai-agent-swagger -DskipTests -B --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/ai-agent-swagger/target/*.jar app.jar

RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]