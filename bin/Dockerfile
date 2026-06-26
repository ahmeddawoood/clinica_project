FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

COPY src ./src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S clinic && adduser -S clinic -G clinic
USER clinic

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port=${PORT:-8082} \
  -jar app.jar
