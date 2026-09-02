FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests -Dspotless.check.skip=true \
    && java -Djarmode=layertools -jar target/video-service-*.jar extract --destination layers


FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S fiapx && adduser -S -G fiapx fiapx

WORKDIR /app

COPY --from=build --chown=fiapx:fiapx /build/layers/dependencies/ ./
COPY --from=build --chown=fiapx:fiapx /build/layers/spring-boot-loader/ ./
COPY --from=build --chown=fiapx:fiapx /build/layers/snapshot-dependencies/ ./
COPY --from=build --chown=fiapx:fiapx /build/layers/application/ ./

USER fiapx

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", \
    "org.springframework.boot.loader.launch.JarLauncher"]
