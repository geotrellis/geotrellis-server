# GeoTrellis Server

A set of components for conveniently serving raster data from arbitrary
sources. This library enables complex workflows which result in
TMS-based (z, x, y) and extent-based products. Throughout the library,
MAML ([Map Algebra Modeling Language](https://github.com/geotrellis/maml/))
serves as the Lingua Franca - generating an endpoint which returns imagery
takes the form of specifying one of
1. a MAML expression + the arguments which complement it
2. a way of producing MAML expressions based on provided arguments
3. a MAML expression to evaluate (once bound with arguments)

## Getting started

### Running geotrellis-server

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
