# GeoTrellis Server

A set of components designed to simplify serving raster data from
arbitrary sources. This library enables complex workflows which
result in TMS-based (z, x, y) and extent-based products.

In addition to providing a story about how sources of imagery can be displayed
or returned, this project aims to ease the creation of dynamic,
responsive layers whose transformations can be described in MAML
([Map Algebra Modeling Language](https://github.com/geotrellis/maml/)).


### Structure:

- geotrellis-server-core: server-agnostic code which facilitates the
  generation of imagery/metadata about imagery/image-based products.
- geotrellis-server-example: a collection of http4s servers which
  illustrate the use of `core` components to solve real problems


### Design Questions

- How can we support a collection of backends? (looks like shapeless
  `coproduct` derivation of typeclass behavior that each underlying type
  implements)


### Distribution
- Publish a generic server example that can actually be useful? Maybe one
  that assumes GT avro layers?


### ????
1. a MAML expression + the arguments which complement it
2. a way of producing MAML expressions based on provided arguments
3. a MAML expression to evaluate (once bound with arguments)


### Running an example

Three example servers are available which can be run through the provided
makefile.

#### WeightedOverlayServer

```bash
make runOverlayExample
```

Blah blah blah

Zim zoom zah

#### PersistenceServer

```bash
make runPersistenceExample
```

Some words. Describing things

#### NdviServer

```bash
make runNdviExample
```

Lookee here, NDVI demo

