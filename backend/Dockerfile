# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Runtime stage - smaller image, no build tooling
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S teacheros && adduser -S teacheros -G teacheros
COPY --from=build /app/target/*.jar app.jar
USER teacheros

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
