# syntax=docker/dockerfile:1
# Build context = repository root (needs the parent POM and messaging-common):
#   docker build -f auth-service/Dockerfile -t messaging/auth-service .

# ---------- 1. Build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
# Cache the Maven repository between builds; build only this module + the modules it depends on
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -pl auth-service -am package -DskipTests

# ---------- 2. Extract layers ----------
# Splits the fat jar into layers (dependencies change rarely, application code often),
# so a code change only rebuilds the small top layer of the image.
FROM eclipse-temurin:25-jre AS extract
WORKDIR /extract
COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination layers

# ---------- 3. Runtime ----------
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app app
USER app
WORKDIR /app
COPY --from=extract /extract/layers/dependencies/ ./
COPY --from=extract /extract/layers/spring-boot-loader/ ./
COPY --from=extract /extract/layers/snapshot-dependencies/ ./
COPY --from=extract /extract/layers/application/ ./
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
