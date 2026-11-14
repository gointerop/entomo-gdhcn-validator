# syntax=docker/dockerfile:1

# ---- build -----------------------------------------------------------------
FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached until the pom changes
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Tests need Docker (Testcontainers) and run in CI; the image build only packages
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/entomo-gdhcn-validator-*.jar extract --layers --launcher --destination /build/layers

# ---- runtime ---------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S gdhcn && adduser -S -G gdhcn -H -s /sbin/nologin gdhcn
WORKDIR /app

# Dependencies change rarely, application classes often: separate layers keep pulls small
COPY --from=build /build/layers/dependencies/ ./
COPY --from=build /build/layers/spring-boot-loader/ ./
COPY --from=build /build/layers/snapshot-dependencies/ ./
COPY --from=build /build/layers/application/ ./

# Local-filesystem profile stores IPS JSON here; mount a volume to keep it
RUN mkdir -p /data/json && chown -R gdhcn:gdhcn /data
ENV FILESYSTEM_BASELOCATION=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER gdhcn
EXPOSE 8082
# Configuration is read from the environment (Spring relaxed binding), e.g.:
#   SPRING_PROFILES_ACTIVE=fileSystem|aws
#   SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD
#   TNG_TLS_PEM, TNG_TLS_KEY (paths to mounted files), TNG_DSC_PRIVATEKEY, TNG_DSC_PRIVATEKEY_KID, TNG_COUNTRY
#   AWS_BUCKET_* for the aws profile
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
