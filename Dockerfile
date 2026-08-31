FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser -u 10001 appuser \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY app.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser

ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
