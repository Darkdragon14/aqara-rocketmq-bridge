FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY service/pom.xml service/pom.xml
RUN mvn -f service/pom.xml -B dependency:go-offline

COPY service service
RUN mvn -f service/pom.xml -B clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /workspace/service/target/aqara-rocketmq-bridge-0.1.0-SNAPSHOT.jar /app/bridge.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/bridge.jar"]
