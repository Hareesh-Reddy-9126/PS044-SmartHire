# SmartHire Application Service — Docker image
# Build: docker build -t smarthire-application-service:local -f services/application-service/Dockerfile .
# Run:   docker run -p 8083:8083 --env-file deploy/.env smarthire-application-service:local

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home appuser

WORKDIR /app

# Copy the built JAR (from Maven target/)
COPY services/application-service/target/smarthire-application-service-*.jar app.jar

# Ownership
RUN chown appuser:appuser app.jar

USER appuser

# JVM tuning for container environments
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8083

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]