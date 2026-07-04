# Build stage - cache dependinte separat ca sa fie mai rapid rebuild-ul
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# user non-root pentru securitate
RUN addgroup -S clinic && adduser -S clinic -G clinic
USER clinic

COPY --from=builder /app/target/*.jar app.jar

# Cloud Run injecteaza PORT, local foloseste 8082
EXPOSE 8080

ENV LANG=C.UTF-8

# folosesc shell form ca sa expandeze ${PORT}
ENTRYPOINT exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8 \
  -Dserver.port=${PORT:-8082} \
  -jar app.jar
