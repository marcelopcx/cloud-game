# game-crud: API del catálogo sobre PostgreSQL. Java puro; ImageMagick para optimizar imágenes.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
COPY resources resources
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
# ImageMagick (convert/identify): achica las imágenes del catálogo y las pasa a WebP (ImageOptimizer).
RUN apt-get update \
    && apt-get install -y --no-install-recommends imagemagick \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/target/game-crud-*.jar /opt/app.jar
EXPOSE 8096
ENTRYPOINT ["java", "-jar", "/opt/app.jar"]
