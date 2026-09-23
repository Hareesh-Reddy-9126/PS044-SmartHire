# SmartHire Recruitment Service — Docker image
# Build: docker build -t smarthire-recruitment-service:local -f services/recruitment-service/Dockerfile .
# Run:   docker run -p 8084:8084 --env-file deploy/.env smarthire-recruitment-service:local

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home appuser

WORKDIR /app

# Copy the built JAR (from Maven target/)
COPY services/recruitment-service/target/smarthire-recruitment-service-*.jar app.jar

# Ownership
RUN chown appuser:appuser app.jar

USER appuser

# JVM tuning for container environments
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8084

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]