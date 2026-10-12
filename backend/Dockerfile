# ---- Stage 1: build the Spring Boot jar ---------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# Download dependencies first (layers cache well when only sources change)
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Stage 2: minimal JRE runtime --------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/eventnest-api-*.jar app.jar
EXPOSE 8080
ENV SERVER_PORT=8080 \
    EVENTNEST_STORAGE_PATH=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]