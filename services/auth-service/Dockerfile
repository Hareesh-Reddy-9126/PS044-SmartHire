# ---- Build stage: compile on the approved JDK 21, independent of the host JVM (ADR-0002) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -pl services/auth-service -am -DskipTests clean package

# ---- Runtime stage: slim JRE, non-root user, health-checked (governance §34) ----
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home appuser
WORKDIR /app
COPY --from=build /build/services/auth-service/target/smarthire-auth-service-*.jar app.jar
USER appuser
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
