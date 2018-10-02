FROM openjdk:8-jre-alpine

COPY geotrellis-server-example.jar /opt/geotrellis-server-example.jar
WORKDIR /opt

ENTRYPOINT ["java", "-cp", "/opt/geotrellis-server-example.jar", "geotrellis.server.example.persistence.PersistenceServer"]
