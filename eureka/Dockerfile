FROM eclipse-temurin:8-jre
LABEL maintainer="nnkwrik nnkwrik@gmail.com"

ENV SPRING_PROFILE_ACTIVE=docker

COPY target/eureka-0.0.1-SNAPSHOT.jar /eureka.jar

ENTRYPOINT ["java","-jar","/eureka.jar"]
