# SmartHire AI Service — Multi-stage Docker Build
# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy parent pom and common module first for dependency resolution
COPY pom.xml .
COPY common/pom.xml common/
COPY services/ai-service/pom.xml services/ai-service/

# Download dependencies (cached layer)
RUN mvn -B -pl services/ai-service -am dependency:go-offline -DskipTests

# Copy source code
COPY common/src common/src
COPY services/ai-service/src services/ai-service/src

# Build the service
RUN mvn -B -pl services/ai-service -am package -DskipTests -Dspotbugs.skip=true

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