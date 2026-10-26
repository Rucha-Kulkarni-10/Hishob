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

# Package the Auth Service artifact
RUN sh ./mvnw clean package -pl services/auth-service -am -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S hishob && adduser -S hishob -G hishob

# Copy the generated JAR
COPY --from=builder /app/services/auth-service/target/*.jar app.jar
RUN chown -R hishob:hishob /app

USER hishob:hishob

ENV PORT=8081
# Free-tier budget for Render's 512 MB container, sized from measured demand on
# this classpath (~96 MB live Metaspace after login/friends/notifications use,
# ~44 MB heap live set). MaxMetaspaceSize is a ceiling, not a reservation.
# Rationale and measurements: DEPLOYMENT_RENDER.md, section 2 item 5.
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms96m -Xmx192m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=32m"

EXPOSE ${PORT}

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -Dserver.port=${PORT} -jar app.jar"]
