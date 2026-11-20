# Step 40 - one Dockerfile for every Spring Boot service.
#
# "Build once, package many": Maven builds and tests all jars first (Jenkins stage or
# `mvn -f backend/pom.xml package`), then each image only copies its jar onto a small JRE.
#
#   docker build -f backend/Dockerfile --build-arg SERVICE=auth-service -t socialhub/auth-service backend
#   docker build -f backend/Dockerfile --build-arg SERVICE=video-worker --target worker -t socialhub/video-worker backend

FROM eclipse-temurin:21-jre-alpine AS service
ARG SERVICE
# Run as an unprivileged user: a compromised process cannot modify the image or the host.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar app.jar
USER app
# Container-aware JVM: heap sized from the container memory limit, UTC everywhere.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom"
# Port is service-specific; health is always /actuator/health on SERVER_PORT.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# The video worker also needs ffmpeg/ffprobe.
FROM service AS worker
USER root
RUN apk add --no-cache ffmpeg
USER app
