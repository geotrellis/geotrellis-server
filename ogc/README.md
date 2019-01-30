# GeoTrellis OGC Services

This project a Scala implementation of [OGC Web Coverage Service](http://www.opengeospatial.org/standards/wcs) that is able to serve contents of GeoTrellis indexed layers.

WMS Implements:
- `GetCapabilities`
- `GetMap`

## Testing

Currently the code can be tested in development environment only, using SBT configuration for this project.

### Configuration
Modify the [`application.conf`](ogc/test/resource/application.conf) to list the GeoTrellis layers that should be available

```
http {
    "interface": "0.0.0.0"
    "interface": ${?HTTP_INTERFACE}
    "port": 5678
    "port": ${?HTTP_PORT}
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

### Run Test Server

Start development server with:

```sh
./sbt "project ogc" "test:run"

[info]   _   _   _        _ _
[info]  | |_| |_| |_ _ __| | | ___
[info]  | ' \  _|  _| '_ \_  _(_-<
[info]  |_||_\__|\__| .__/ |_|/__/
[info]              |_|
[info] [ForkJoinPool-1-worker-5] INFO org.http4s.server.blaze.BlazeServerBuilder - http4s v0.19.0 on blaze v0.14.0-M5 started at http://[0:0:0:0:0:0:0:0]:5678/
```

Connect to server from QGIS using URL: `http://localhost:5678/wms?`