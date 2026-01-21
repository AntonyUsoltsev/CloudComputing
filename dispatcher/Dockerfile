FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY dispatcher/pom.xml ./dispatcher/
COPY model/pom.xml ./model/
COPY common/pom.xml ./common/
COPY client-api/pom.xml ./client-api/

RUN mkdir -p ./client-api/src/main/java && \
    mkdir -p ./client-api/src/main/resources && \
    mkdir -p ./worker/src/main/java && \
    mkdir -p ./worker/src/main/resources && \
    echo '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><parent><groupId>ru.nsu</groupId><artifactId>CloudComputing</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>worker</artifactId></project>' > ./worker/pom.xml

RUN mvn dependency:go-offline -pl dispatcher -am || true

COPY model ./model
COPY common ./common
COPY dispatcher ./dispatcher

RUN mvn clean package -DskipTests -pl dispatcher -am

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/dispatcher/target/*-jar-with-dependencies.jar app.jar

EXPOSE 8080

