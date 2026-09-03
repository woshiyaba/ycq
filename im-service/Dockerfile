FROM eclipse-temurin:8-jre
LABEL maintainer="nnkwrik nnkwrik@gmail.com"

ENV SPRING_PROFILE_ACTIVE=docker

COPY target/im-service-0.0.1-SNAPSHOT.jar /im-service.jar

ENTRYPOINT ["java","-jar","/im-service.jar"]
