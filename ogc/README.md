# GeoTrellis OGC Services

This project a Scala implementation of [OGC Web Coverage Service](http://www.opengeospatial.org/standards/wcs) that is able to serve contents of GeoTrellis indexed layers.

WMS Implements:
- `GetCapabilities`
- `GetMap`

## Testing

Currently the code can be tested in development environment only, using SBT configuration for this project.

### Configuration
Modify the [`application.conf`](ogc/main/resource/application.conf) to list the GeoTrellis layers that should be available

```
http {
    "interface": "0.0.0.0"
    "interface": ${?HTTP_INTERFACE}
    "port": 5678
    "port": ${?HTTP_PORT}
}

service {
    "url": ${?SERVICE_URL}
}

layers = [
    {
        catalog-uri = "s3://azavea-datahub/catalog"
        name = "nlcd-2011-epsg3857"
        zoom = 13
        band-count = 1
    },
    {
        catalog-uri = "s3://azavea-datahub/catalog"
        name = "us-census-median-household-income-30m-epsg3857"
        zoom = 12
        band-count = 1
    }
]
```

`zoom` parameter should indicate the highest zoom level available in the catalog for that layer.

Above configuration respects `HTTP_INTERFACE` and `HTTP_PORT` environment variables and will use their values when available.
`SERVICE_URL` environment variable provides an override when publicly reachable URL is not product of interface IP and port.

### Run Test Server

Start development server with:

```sh
> ./sbt "project ogc" "run"

# [info]   _   _   _        _ _
# [info]  | |_| |_| |_ _ __| | | ___
# [info]  | ' \  _|  _| '_ \_  _(_-<
# [info]  |_||_\__|\__| .__/ |_|/__/
# [info]              |_|
# [info] [ForkJoinPool-1-worker-5] INFO org.http4s.server.blaze.BlazeServerBuilder - http4s v0.19.0 on blaze v0.14.0-M5 started at http://[0:0:0:0:0:0:0:0]:5678/
```

Connect to server from QGIS using URL: `http://localhost:5678/wms?`

### Docker Image

This project can produces a Docker image as configured by [`docker.sbt`](ogc/docker.sbt).

To generate the image run:

```sh
> ./sbt "project ogc" docker

# ...
# [info] Assembly up to date: /Users/user/proj/geotrellis-server/ogc/target/scala-2.11/geotrellis-server-ogc.jar
# [info] Sending build context to Docker daemon  103.5MB
# [info] Step 1/3 : FROM openjdk:8-jre
# [info]  ---> dd20fb277e3c
# [info] Step 2/3 : ADD 0/geotrellis-server-ogc.jar /app/geotrellis-server-ogc.jar
# [info]  ---> Using cache
# [info]  ---> 3bf0ba9f0a13
# [info] Step 3/3 : ENTRYPOINT ["java", "-jar", "\/app\/geotrellis-server-ogc.jar"]
# [info]  ---> Using cache
# [info]  ---> 1d7130eba160
# [info] Successfully built 1d7130eba160
# [info] Tagging image 1d7130eba160 with name: geotrellis/geotrellis-server-ogc:latest
# [info] Tagging image 1d7130eba160 with name: geotrellis/geotrellis-server-ogc:v0.0.14
# [success] Total time: 9 s, completed Feb 1, 2019 12:56:56 PM
```

The image can be run with:

```sh
docker run --rm -it -e AWS_REGION=us-east-1 -e SERVICE_URL="http://localhost:5678/wms" -v ~/.aws:/root/.aws -p 5678:5678 geotrellis/geotrellis-server-ogc:latest

# [ForkJoinPool-1-worker-5] INFO geotrellis.server.ogc.Server$ - Advertising service URL at http://localhost:5678/wms
# [ForkJoinPool-1-worker-5] INFO org.http4s.blaze.channel.nio1.NIO1SocketServerGroup - Service bound to address /0.0.0.0:5678
# [ForkJoinPool-1-worker-5] INFO org.http4s.server.blaze.BlazeServerBuilder -
#   _   _   _        _ _
#  | |_| |_| |_ _ __| | | ___
#  | ' \  _|  _| '_ \_  _(_-<
#  |_||_\__|\__| .__/ |_|/__/
#              |_|
# [ForkJoinPool-1-worker-5] INFO org.http4s.server.blaze.BlazeServerBuilder - http4s v0.19.0 on blaze v0.14.0-M5 started at http://0.0.0.0:5678/
```

Notice that the `SERVICE_URL` environment variable is given to prevent the service from reporting IP address of docker private network interface as its service endpoint.