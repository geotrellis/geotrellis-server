# GeoTrellis OGC Services

This is a scala project that is able to serve contents of Geotrellis
indexed layers and transformations of said layers which can be described
with the help of the Map Algebra Modeling Language (MAML) in accordance
with certain OGC standards. The currently supported standards are
[OGC Web Coverage Service](http://www.opengeospatial.org/standards/wcs),
[OGC Web Map Service](https://www.opengeospatial.org/standards/wms),
and [OGC Web Map Tile Service](https://www.opengeospatial.org/standards/wmts).

These OGC services provide different ways of viewing rasters and related
metadata. Consequently, each layer presented by a given service requires
that appropriate configuration be provided *at the start of the
application*. To get started with a demonstration configuration and
demonstration layers, check out [usage.md](./usage.md). Once comfortable
with starting the server and interacting with the resources it provides,
modification of the configuration to serve your own layers is a logical
next step which is documented in [conf.md](./conf.md). If the evaluation
of complex layers with MAML requires special tuning, look to
[maml.md](./maml.md) for an explanation of how MAML ASTs (which are
used directly in the configuration file to support complex, map algebra
layers) are interpreted.

### Contents

- [Starting the Server](usage.md)
- [Modifying Configuration](conf.md)
- [Custom MAML](maml.md)
- [Available Maml Operations](maml-operations.md)


