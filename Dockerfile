# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime - use Playwright's official image (browsers + deps pre-installed)
# Matches Playwright 1.41.0 from pom.xml
FROM mcr.microsoft.com/playwright/java:v1.41.0-jammy

WORKDIR /app

# Copy and extract the fat WAR (avoids nested JAR issues with Playwright driver)
COPY --from=build /app/target/*.war app.war
RUN mkdir -p /app/extracted && cd /app/extracted && jar -xf /app/app.war && rm /app/app.war

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-cp", "/app/extracted", "org.springframework.boot.loader.launch.WarLauncher"]
