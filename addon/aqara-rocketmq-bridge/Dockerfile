ARG BUILD_FROM
FROM ${BUILD_FROM} AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven openjdk-17-jdk ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY service/pom.xml service/pom.xml
RUN mvn -f service/pom.xml -B dependency:go-offline
COPY service service
RUN mvn -f service/pom.xml -B clean package -DskipTests

FROM ${BUILD_FROM}

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless bash jq curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /workspace/service/target/aqara-rocketmq-bridge-0.1.0-SNAPSHOT.jar /app/bridge.jar
COPY addon/aqara-rocketmq-bridge/run.sh /run.sh

RUN chmod a+x /run.sh

CMD ["/run.sh"]
