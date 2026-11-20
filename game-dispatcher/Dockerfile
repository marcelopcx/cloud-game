# Dispatcher: Java puro; habla HTTP con el loader, el crud y las estaciones.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
COPY --from=build /src/target/game-dispatcher-*.jar /opt/app.jar
EXPOSE 8094
ENTRYPOINT ["java", "-jar", "/opt/app.jar"]
