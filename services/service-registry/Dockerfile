# ---- Build stage: compile on the approved JDK 21, independent of the host JVM (ADR-0002) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Context is the repo root (see .dockerignore for exclusions).
COPY . .
# Build this service and the modules it depends on. Tests run in the host quality gate,
# not during image assembly (Testcontainers requires a Docker daemon).
RUN mvn -B -pl services/service-registry -am -DskipTests clean package

# ---- Runtime stage: slim JRE, non-root user, health-checked (governance §34) ----
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home appuser
WORKDIR /app
COPY --from=build /build/services/service-registry/target/smarthire-service-registry-*.jar app.jar
USER appuser
EXPOSE 8761
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
