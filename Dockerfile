# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy

# Install wget for the HEALTHCHECK command.
RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -g 1000 appgroup && \
    useradd -u 1000 -g appgroup -m -s /usr/sbin/nologin appuser

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD wget -q -O- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

