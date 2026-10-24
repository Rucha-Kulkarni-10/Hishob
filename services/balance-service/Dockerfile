# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper, root POM, and configuration
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy all module POMs to satisfy Maven reactor
COPY gateway/api-gateway/pom.xml gateway/api-gateway/pom.xml
COPY infrastructure/discovery-server/pom.xml infrastructure/discovery-server/pom.xml
COPY infrastructure/config-server/pom.xml infrastructure/config-server/pom.xml
COPY services/auth-service/pom.xml services/auth-service/pom.xml
COPY services/group-service/pom.xml services/group-service/pom.xml
COPY services/expense-service/pom.xml services/expense-service/pom.xml
COPY services/balance-service/pom.xml services/balance-service/pom.xml
COPY services/settlement-service/pom.xml services/settlement-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml

# Copy full source tree
COPY . .

# Package the Balance Service artifact
RUN ./mvnw clean package -pl services/balance-service -am -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S hishob && adduser -S hishob -G hishob

# Copy the generated JAR
COPY --from=builder /app/services/balance-service/target/*.jar app.jar
RUN chown -R hishob:hishob /app

USER hishob:hishob

ENV PORT=8084
ENV JAVA_OPTS="-XX:+UseSerialGC -Xmx220m -Xms128m -XX:MaxMetaspaceSize=96m"

EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -Dserver.port=${PORT} -jar app.jar"]
