FROM eclipse-temurin:8-jre
LABEL maintainer="nnkwrik nnkwrik@gmail.com"

ENV SPRING_PROFILE_ACTIVE=docker

COPY target/user-service-0.0.1-SNAPSHOT.jar /user-service.jar

ENTRYPOINT ["java","-jar","/user-service.jar"]
