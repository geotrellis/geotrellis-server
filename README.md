### Overview

GeoTrellis Server is a set of components designed to simplify
viewing, processing, and serving raster data from arbitrary sources
with an emphasis on doing so in a functional style. It aims to ease
the pains related to constructing complex raster processing workflows
which result in TMS-based (z, x, y) and extent-based products.

In addition to providing a story about how sources of imagery can be displayed
or returned, this project aims to simplify the creation of dynamic,
responsive layers whose transformations can be described in MAML
([Map Algebra Modeling Language](https://github.com/geotrellis/maml/)).


### Including Geotrellis Server

Current version:
 - 0.0.2

`libraryDependencies += "com.azavea" %% "geotrellis-server-core" % "0.0.2"`


### Running an example

Three example servers are available which can be run through the provided
makefile.

1. A server and simple UI that evaluates weighted overlays between
arbitrary COGs
```bash
make serveOverlayExample
```

2. Integrates GTServer components with application-specific persistence needs.
```bash
make servePersistenceExample
```

3. Illustrates GTServer evaluating a remote-sensing classic, the NDVI.
```bash
make serveNdviExample
```

