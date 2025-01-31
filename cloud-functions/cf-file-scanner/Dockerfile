FROM maven:3.8-jdk-11-slim AS build

WORKDIR /build

COPY pom.xml pom.xml
RUN mvn -f pom.xml --batch-mode dependency:go-offline

COPY src src
COPY checkstyle.xml checkstyle.xml

# Would like to run these with --offline, but the checkstyle plugin breaks things.
RUN mvn -f pom.xml --batch-mode clean install
RUN mvn -f pom.xml --batch-mode dependency:copy \
    -DoutputDirectory="/app" \
    -Dartifact='org.broadinstitute.ddp.cf:cf-file-scanner:1.0-SNAPSHOT'
RUN mvn -f pom.xml --batch-mode dependency:copy \
    -DoutputDirectory="/app" \
    -Dartifact='com.google.cloud.functions.invoker:java-function-invoker:1.1.0'

FROM clamav/clamav:0.104 AS runtime

ENV GCP_PROJECT=""
ENV RESULT_TOPIC=""
ENV DDP_CLAMAV_SERVER="127.0.0.1:3310"
ENV PORT="8080"

RUN apk add --no-cache \
  openjdk11-jre-headless

COPY --from=build /app /app
COPY --from=build /build/src/main/resources/container/clamd.conf /etc/clamav/clamd.conf
COPY --from=build /build/src/main/resources/container/entrypoint.sh /app/entrypoint.sh

WORKDIR /app

ENTRYPOINT [ "/app/entrypoint.sh" ]