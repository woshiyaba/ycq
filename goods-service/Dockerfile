FROM eclipse-temurin:8-jre
LABEL maintainer="nnkwrik nnkwrik@gmail.com"

ENV SPRING_PROFILE_ACTIVE=docker

COPY target/goods-service-0.0.1-SNAPSHOT.jar /goods-service.jar

ENTRYPOINT ["java","-jar","/goods-service.jar"]
