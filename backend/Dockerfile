# syntax=docker/dockerfile:1
# Production image of the VEYA API. Build from the repository root (the contract docs/api/openapi.yaml is packaged
# into the jar and served at /v3/api-docs):
#
#   docker build -f backend/Dockerfile -t veya-api .
#
# Tests are not run here: they need Docker (Testcontainers) and run in CI (./mvnw verify) before an image is built.
# No secret is baked into the image; database passwords and the JWT key come from the environment at runtime.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
COPY docs/api/openapi.yaml docs/api/openapi.yaml
RUN sed -i 's/\r$//' backend/mvnw backend/.mvn/wrapper/maven-wrapper.properties \
    && cd backend && ./mvnw -B -ntp -q dependency:go-offline -DexcludeReactor=true || true
COPY backend/src backend/src
RUN cd backend && ./mvnw -B -ntp -DskipTests -Djacoco.skip=true package \
    && cp target/backend-*.jar /src/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S veya && adduser -S -G veya -H veya
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
USER veya
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Readiness = application context started, Flyway migrations applied and the database reachable (US-32, NFR-17).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
