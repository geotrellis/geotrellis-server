package geotrellis.server.core.maml

import metadata._
import HasRasterExtents.ops._

import com.azavea.maml.util.Vars
import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._
import cats._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.implicits._
import geotrellis.vector.Extent
import geotrellis.vector.io._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.Tile

import scala.util.Random

object MamlHistogram extends LazyLogging {

  final def sampleRasterExtent(uberExtent: Extent, cs: CellSize, maxCells: Int): Extent = {
    val newWidth = math.sqrt(maxCells.toDouble) * cs.width
    val newHeight = math.sqrt(maxCells.toDouble) * cs.height

    val wDiff = uberExtent.width - newWidth
    val hDiff = uberExtent.height - newHeight

    val xmin = Random.nextDouble * wDiff
    val ymin = Random.nextDouble * hDiff

    Extent(
      xmin + uberExtent.xmin,
      ymin + uberExtent.ymin,
      xmin + newWidth + uberExtent.xmin,
      ymin + newHeight + uberExtent.ymin
    )
  }


  // Choose a native level of representation
  final def chooseCellSize(nativeCellSizes: NEL[CellSize]): CellSize =
    nativeCellSizes
      .reduceLeft({ (chosenCS: CellSize, nextCS: CellSize) =>
        val chosenSize = chosenCS.height * chosenCS.width
        val nextSize = nextCS.height * nextCS.width

        if (nextSize > chosenSize)
          nextCS
        else
          chosenCS
      })


  case class NoSuitableHistogramResolution(cells: Int) extends Throwable
  case class RequireIntersectingSources() extends Throwable

  // Provide IOs for both expression and params, get back a tile
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: MamlExtentReification[Param],
             extended: HasRasterExtents[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ): IO[Interpreted[Histogram[Double]]] =
    for {
      params           <- getParams
      rasterExtents    <- NEL.fromListUnsafe(params.values.toList)
                            .map(_.rasterExtents)
                            .parSequence
                            .map(_.flatten)
      _                <- IO.pure(println(params.values.toList.map(_.crs).parSequence.unsafeRunSync))
      intersection     <- IO { rasterExtents.foldLeft(Option.empty[Extent])({ (mbExtent, re) =>
                            mbExtent match {
                              case Some(extent) =>
                                extent.intersection(re.extent)
                              case None =>
                                Some(re.extent)
                            }
                          }).getOrElse(throw new RequireIntersectingSources()) }
      cellSize         <- IO { chooseCellSize(rasterExtents.map(_.cellSize)) }
      sampleExtent     <- IO { sampleRasterExtent(intersection, cellSize, maxCells) }
      _                <- IO.pure(println(s"all: ${Extent.toPolygon(intersection).toGeoJson}, subset: ${Extent.toPolygon(sampleExtent).toGeoJson}"))
      tileForExtent    <- IO { MamlExtent(getExpression, getParams, interpreter) }
      _                <- IO.pure(println("tile4Extent produced"))
      interpretedTile  <- tileForExtent(sampleExtent, cellSize)
      _                <- IO.pure(println("interpretedTile produced"))
    } yield interpretedTile.map(StreamingHistogram.fromTile(_))

  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: MamlExtentReification[Param],
             extended: HasRasterExtents[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter, maxCells)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[Param](
    expr: Expression,
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: MamlExtentReification[Param],
             extended: HasRasterExtents[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ): (Map[String, Param]) => IO[Interpreted[Histogram[Double]]] =
    (paramMap: Map[String, Param]) => {
      apply[Param](IO.pure(expr), IO.pure(paramMap), interpreter, maxCells)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[Param](
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: MamlExtentReification[Param],
             extended: HasRasterExtents[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ) = curried(RasterVar("identity"), interpreter, maxCells)

}

