FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY worker/pom.xml ./worker/
COPY model/pom.xml ./model/
COPY common/pom.xml ./common/
COPY client-api/pom.xml ./client-api/

RUN mkdir -p ./client-api/src/main/java && \
    mkdir -p ./client-api/src/main/resources && \
    mkdir -p ./dispatcher/src/main/java && \
    mkdir -p ./dispatcher/src/main/resources && \
    echo '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><parent><groupId>ru.nsu</groupId><artifactId>CloudComputing</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>dispatcher</artifactId></project>' > ./dispatcher/pom.xml

RUN mvn dependency:go-offline -pl worker -am || true

COPY model ./model
COPY common ./common
COPY worker ./worker

RUN mvn clean package -DskipTests -pl worker -am

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/worker/target/*-jar-with-dependencies.jar app.jar

EXPOSE 8081

