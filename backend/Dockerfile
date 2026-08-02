# ── Stage 1: build ────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src

# Download dependencies first (layer cache friendly)
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn dependency:go-offline -q

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy fat jar
COPY --from=builder /build/target/*.jar app.jar

# Directories the app writes to
RUN mkdir -p /app/uploads /var/log/knowledge-assistant && \
    chown -R appuser:appgroup /app /var/log/knowledge-assistant

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
