# GeoTrellis Server

A lightweight server for serving out GeoTrellis data in a variety of formats.

## Getting started

### Running geotrellis-server

#### Using Docker [TODO]

```bash
docker run -it -p 8080:8080 quay.io/geotrellis/geotrellis-server s3://TODO/TODO
```

#### Running natively [TODO]


### WCS

### Developing

[TODO - Chart of dev machine dependencies]
sbt
docker

#### Running the server in SBT

In the sbt console:

```
> project server
> run s3://bucket/prefix/catalog
```

#### Running with docker-compose



### Deploying

[TODO - Chart of dependencies]
terraform
awscli

# TODO

### Structure:

- geotrellis-server-core: server-agnostic code.
- geotrellis-server-routes: library of akka routes, that you can pull into your own projects.
- geotrellis-server: runnable web server packaged with each GeoTrellis backend capability.

Docker container builds in akka-server


### Design Questions

- How do we support the various backends in a way that doesn't require one server have all the bloat of every backend?
- How can we support a subset of backends? Community pluggable backends?

### Code

- Finish CRS parsing for WCS

### Dev setup
- Fix Akka running in SBT

### Distribution
- Publish docker image and instructions for running
- Publish binaries and instructions for running
