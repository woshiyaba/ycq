FROM eclipse-temurin:8-jre
LABEL maintainer="nnkwrik nnkwrik@gmail.com"

ENV SPRING_PROFILE_ACTIVE=docker

COPY target/auth-service-0.0.1-SNAPSHOT.jar /auth-service.jar

ENTRYPOINT ["java","-jar","/auth-service.jar"]
