FROM openjdk:8-jre-alpine

COPY geotrellis-server-http4s.jar /opt/geotrellis-server-http4s.jar
WORKDIR /opt

ENTRYPOINT ["java", "-jar", "/opt/geotrellis-server-http4s.jar"]
