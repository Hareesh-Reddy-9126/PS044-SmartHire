# syntax=docker/dockerfile:1.7
# SmartHire Notification Service — Docker image
# Build: docker build -t smarthire-notification-service:local -f services/notification-service/Dockerfile .
# Run:   docker run -p 8086:8086 --env-file deploy/.env smarthire-notification-service:local

# ---- Build stage: compile on the approved JDK 21, independent of the host JVM (ADR-0002) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
# Compile the whole reactor once. BuildKit reuses this layer across every service image built
# from the same context, so a cold stack build compiles Maven once instead of once per service
# (the difference between minutes and hours on the 2-OCPU production VM).
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true -Dspotbugs.skip=true package

# ---- Runtime stage: slim JRE, non-root user, health-checked ----
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home appuser

WORKDIR /app
COPY --from=build /build/services/notification-service/target/smarthire-notification-service-*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

# ENV JAVA_TOOL_OPTIONS is honoured by the JVM without an intermediate shell.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8086

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
