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
 - 0.1.10

Add the geotrellis-server dependency by declaring it within your
project's `build.sbt`:
`libraryDependencies += "com.azavea" %% "geotrellis-server-core" % "0.1.10"`


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
// This node describes a `LocalAdd` on the values eventually
// bound to `RasterVar("test1")` and `RasterVar("test2")
val simpleAdd = Addition(List(RasterVar("test1"), RasterVar("test2")))

// This describes incrementing every value in the eventually bound raster by 1
val plusOne = Addition(List(RasterVar("additionRaster1"), IntLit(1)))
```

Because MAML encodes map alagebra transformations in data, actually
executing a MAML program for practical purposes can be difficult and
unintuitive. GeoTrellis server bridges this gap by providing a
typeclass-based means of extending source types in client applications
with the behaviors necessary to actually evaluate a MAML AST.

The following example demonstrates what is required to generate
functions which produce extents provided MAML program descriptions. For
a more complete example, check out
[CogNode](example/src/main/scala/geotrellis/server/example/cog/CogNode.scala).
```scala
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats._
import cats.effect._
import com.azavea.maml.ast._
import com.azavea.maml.eval.BufferingInterpreter
import geotrellis.server._

// This class points to a COG and specifies a band of interest
case class RasterRef(uri: URI, band: Int)

// We need to provide some implicit evidence. Most applications can do this within companion objects
object RasterRef {
  // reification means 'thingification', and that's what we're proving we can do here
  implicit val rasterRefExtentReification: ExtentReification[RasterRef] = new ExtentReification[RasterRef] {
    def extentReification(self: CogNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] = ???
  }
  // We can lean on circe's automatic derivation to provide an encoder
  implicit val rasterRefEncoding: Encoder[RasterRef] = deriveEncoder[RasterRef]
}

// A source from which MAML evaluation will be able to derive necessary artifacts
val reference = RasterRef("http://some.url.com", 1)

// We need to key provided references based on the ID of the Var they'll replace
val parameters = Map("additionRaster1" -> reference)

// This is an interpeter GT Server will use to roll up the tree + params to some result
val interpreter = BufferingInterpreter.DEFAULT

// Not yet a result: we can use the result here to produce artifacts for different extent inputs
val tileEval = LayerExtent.apply(IO.pure(plusOne), IO.pure(parameters), interpreter)

val targetExtent: Extent = ??? // Where should the tile come from?
val targetCellSize: CellSize = ??? // What resolution should the tile be?

// Branch on Valid/Invalid and print some info about which branch we're on
tileEval(targetExtent, targetCellSize) map {
  case Valid(tile) =>
    println("we did it, a tile: (rows: ${tile.rows}, cols: ${tile.cols})")
  case Invalid(err) =>
    println("Ran into an error (${err.asJson}) during MAML evaluation of AST (${plusOne.asJson}) with params (${params.asJson})")
}
```

`LayerExtent` is joined by two other objects which organize evaluation
strategies for different products:

- `LayerExtent`: Constructs functions that produce a `cats.data.Validated` instance
containing a `Tile` (given an extent) or else `MamlError`s. Requires
`ExtentReification` and `Encoder` evidence

- `LayerTms`: Constructs functions that produce a `cats.data.Validated` instance
containing a `Tile` (given the tms Z, X, Y coordinates) or else
`MamlError`s. Requires `TmsReification` and `Encoder` evidence

- `LayerHistogram`: Constructs functions that produce a `cats.data.Validated` instance
containing a `Histogram` or else `MamlError`s. Requires
`ExtentReification`, `Encoder`, and `HasRasterExtents` evidence

Each of these objects is a response to distinct needs encountered when
writing raster-based applications. Included in each object are methods
which encode several strategies for evaluating their products. The strategies
currently available are:
- `apply`: Takes parameters, a MAML AST, and a MAML `Interpreter` and evaluates accordingly

- `generateExpression`: parameters, a function which will generate an AST based on the
parameters, and a MAML `Interpreter`

- `curried`: Takes an AST and a MAML `Interpreter` (this method produces an
intermediate, curried, function which expects a parameter map to
evaluate)

- `identity`: Evaluates a proven source without any MAML evaluation (useful for
quickly defining a static layer viewer or debugging implicit evidence behavior


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

