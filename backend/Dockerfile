# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /data/uploads && chown -R app:app /data
WORKDIR /app
COPY --from=build /src/target/claimflow-api.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError" \
    UPLOAD_DIR=/data/uploads
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
