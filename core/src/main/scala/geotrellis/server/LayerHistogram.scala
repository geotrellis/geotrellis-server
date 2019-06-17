package geotrellis.server

import geotrellis.server.extent.SampleUtils
import HasRasterExtents.ops._

import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.eval._
import geotrellis.vector.Extent
import geotrellis.raster._
import geotrellis.raster.histogram._

import com.typesafe.scalalogging.LazyLogging
import cats._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.implicits._
import cats.syntax.either._

import scala.collection.mutable


object LayerHistogram extends LazyLogging {

  case class NoSuitableHistogramResolution(cells: Int) extends Throwable
  case class RequireIntersectingSources() extends Throwable

  // Provide IOs for both expression and params, get back a tile
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: Interpreter[IO],
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             contextShift: ContextShift[IO]
  ): IO[Interpreted[List[Histogram[Double]]]] =
    for {
      params            <- getParams
      rasterExtents     <- NEL.fromListUnsafe(params.values.toList)
                             .map(_.rasterExtents)
                             .parSequence
                             .map(_.flatten)
      intersection      <- IO { SampleUtils.intersectExtents(rasterExtents.map(_.extent))
                                  .getOrElse(throw new RequireIntersectingSources()) }
      _                 <- IO { logger.debug(s"[LayerHistogram] Intersection of provided layer extents calculated: $intersection") }
      cellSize          <- IO { SampleUtils.chooseLargestCellSize(rasterExtents.map(_.cellSize)) }
      _                 <- IO { logger.debug(s"[LayerHistogram] Largest cell size of provided layers calculated: $cellSize") }
      mbtileForExtent   <- IO { LayerExtent(getExpression, getParams, interpreter) }
      _                 <- IO { logger.debug(s"[LayerHistogram] calculating histogram from (approximately) ${intersection.area / (cellSize.width * cellSize.height)} cells") }
      interpretedTile   <- mbtileForExtent(intersection, cellSize)
    } yield {
      interpretedTile.map { mbtile =>
        mbtile.bands.map { band  => StreamingHistogram.fromTile(band) }.toList
      }
    }

  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: Interpreter[IO],
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             contextShift: ContextShift[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter, maxCells)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[Param](
    expr: Expression,
    interpreter: Interpreter[IO],
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             contextShift: ContextShift[IO]
  ): (Map[String, Param]) => IO[Interpreted[List[Histogram[Double]]]] =
    (paramMap: Map[String, Param]) => {
      apply[Param](IO.pure(expr), IO.pure(paramMap), interpreter, maxCells)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[Param](
    param: Param,
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             contextShift: ContextShift[IO]
  ) = {
    val eval = curried(RasterVar("identity"), ConcurrentInterpreter.DEFAULT, maxCells)
    eval(Map("identity" -> param))
  }

}
