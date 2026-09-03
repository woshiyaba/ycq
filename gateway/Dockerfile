FROM eclipse-temurin:8-jre
LABEL maintainer="nnkwrik nnkwrik@gmail.com"

ENV SPRING_PROFILE_ACTIVE=docker

COPY target/gateway-0.0.1-SNAPSHOT.jar /gateway.jar

ENTRYPOINT ["java","-jar","/gateway.jar"]
