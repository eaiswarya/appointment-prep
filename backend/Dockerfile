# TECHNICAL_DESIGN §12.1

# Pinned to the wrapper's Maven version; using the image's Maven avoids downloading it during the build.
FROM maven:3.10.0-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first, so source-only changes reuse the cached layer.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/api/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
