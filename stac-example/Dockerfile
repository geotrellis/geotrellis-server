FROM openjdk:11-jre-slim

COPY ./target/scala-2.12/geotrellis-server-stac-example.jar /var/lib/geotrellis-server/

WORKDIR /var/lib/geotrellis-server

ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "geotrellis-server-stac-example.jar"]
