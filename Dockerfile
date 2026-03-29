# ── Stage 1: Build ──
FROM maven:3-eclipse-temurin-20 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -q -DskipTests

# ── Stage 2: Runtime ──
FROM eclipse-temurin:20-jre
WORKDIR /app
COPY --from=build /app/target/leetcli-1.0-SNAPSHOT.jar leetcli.jar

# Ensure the container has a proper terminal for JLine
ENV TERM=xterm-256color

ENTRYPOINT ["java", "-jar", "leetcli.jar"]
