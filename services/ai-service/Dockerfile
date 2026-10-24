# SmartHire AI Service — Multi-stage Docker Build
# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy parent pom and strip modules section for standalone build
COPY pom.xml .
RUN sed -i '/<modules>/,/<\/modules>/d' pom.xml

# Copy common and ai-service modules
COPY common/pom.xml common/
COPY common/src common/src
COPY services/ai-service/pom.xml services/ai-service/
COPY services/ai-service/src services/ai-service/src

# Install parent pom locally
RUN mvn -B -f pom.xml install -N -DskipTests -Dspotbugs.skip=true

# Apply Spotless formatting to common module (fixes CRLF/LF issues)
RUN mvn -B -f common/pom.xml spotless:apply -DskipTests -Dspotbugs.skip=true

# Install common module locally first (required by ai-service)
RUN mvn -B -f common/pom.xml install -DskipTests -Dspotbugs.skip=true

# Build the service
RUN mvn -B -f services/ai-service/pom.xml package -DskipTests -Dspotbugs.skip=true

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/services/ai-service/target/*.jar app.jar

# Run as non-root user
RUN addgroup -g 1000 smarthire && adduser -D -u 1000 -G smarthire smarthire
USER smarthire

EXPOSE 8087

# Health check
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=60s \
  CMD curl -fsS http://localhost:8087/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]