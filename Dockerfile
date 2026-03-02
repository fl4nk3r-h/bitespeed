# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven wrapper and POM first (layer caching for dependencies)
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build the JAR
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 2: Run ----
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/bitespeed-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT as an env var (default 8080)
ENV PORT=8080
EXPOSE ${PORT}

# Run with container-aware memory settings
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
