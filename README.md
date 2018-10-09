### About the library

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
 - 0.0.4

Add the geotrellis-server dependency by declaring it within your
project's `build.sbt`:
`libraryDependencies += "com.azavea" %% "geotrellis-server-core" % "0.0.4"`


### High level concepts

Imagine you've got a simple case class which is sufficient to identify
layers of imagery for an application you're working on. 

```scala
import java.net.URI

case class ImageryLayer(location: URI)
```

Imagine further that you'd like to enable your application to compose these
layers together via map algebra to produce derived layers and that the
combinations can't be known at compile-time (users will be deciding
which - if any - map algebra to run). This is a job that GeoTrellis
server can radically simplify. Because we're dealing with behavior
specified at runtime, we need to evaluate program descriptions rather
than simple, first-order parameters - the task for which
[MAML](https://github.com/geotrellis/maml/) was written.

```scala
// This addition node describes a `LocalAdd` on the values eventually
// bound to `RasterVar("test1")` and `RasterVar("test2")
val simpleAdd = Addition(List(RasterVar("test1"), RasterVar("test2")))

// This describes incrementing every value in the eventually bound raster by 1
val plusOne = Addition(List(RasterVar("test1"), IntLit(1)))
```

Three objects are worth calling out immediately:
1. [MamlExtent](core/src/main/scala/geotrellis/server/core/MamlExtent.scala)
> produces a `Validated` `Tile` (given an extent) which can be served out
> directly or else an `Invalid` `MamlError`, which can be converted to
> JSON as is convenient.
2. [MamlTms](core/src/main/scala/geotrellis/server/core/MamlTms.scala)
> produces a `Validated` `Tile` (given the tms Z, X, Y
> coordinates) which can be served out directly or else an `Invalid`
> `MamlError`, which can be converted to JSON as is convenient.
3. [MamlHistogram](core/src/main/scala/geotrellis/server/core/MamlHistogram.scala)
> attempts to sample the derived layer to generate a histogram of its
> contained values.

Each of these objects is a response to distinct needs encountered when
writing raster-based applications. Included are  several strategies for
evaluating their products. The strategies currently available are:
1. `apply`
> Takes: parameters, AST, and a MAML `Interpreter`
2. `generateExpression`
> Takes: parameters, a function which will generate an AST based on the
> parameters, and a MAML `Interpreter`
3. `curried`
> Takes: an AST and a MAML `Interpreter` (this method produces an
> intermediate, curried, function which expects a parameter map to
> evaluate)
4. `identity`
> Takes an interpreter and evaluates the simplest image-based MAML tree
> a lone `RasterVar` with no transformations.

Here's a quick NDVI example using GT Server:
```scala
// The MAML AST
val ast =
  Addition(List(
    RasterVar("nir"),
    RasterVar("red")
  ))

// The parameter bindings corresponding to instances of `Var` in the AST to evaluate
val params =
  Map(
    "nir" -> ImageryLayer("http://some.source"),
    "red" -> ImageryLayer("http://some.other.source")
  )

// Produce the function which will evaluate tiles
val tmsEvaluator = MamlTms.apply(IO.pure(ast), IO.pure(params))

// Get z, x, y somehow - usually this is programmatic
val (z, x, y) = ???

// Apply the z, x, y params to the generated evaluator function
val res: IO[Valid[Tile]] = tmsEvaluator(z, x, y)

// We can log the error's JSON representation or else return the successful result
res map {
  case Valid(tile) => tile
  case Invalid(e) => log.debug(e.asJson)
}
```


### Running an example

Three example servers are available which can be run through the provided
makefile. These examples have been implemented via [http4s](https://http4s.org/),
which has a pleasant, clean API and plays nicely with the
[cats](https://typelevel.org/cats/) and [cats-effect](https://typelevel.org/cats-effect/)
libraries.

1. A server and simple UI that evaluates weighted overlays between
arbitrary COGs. This demo includes a simple UI, available at http://localhost:9000/ for a
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

