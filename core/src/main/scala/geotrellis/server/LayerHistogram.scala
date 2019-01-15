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
import cats.effect._
import cats.implicits._
import cats.syntax.either._
import cats.data.{NonEmptyList => NEL}

import scala.collection.mutable


object LayerHistogram extends LazyLogging {

  case class NoSuitableHistogramResolution(cells: Int) extends Throwable
  case class RequireIntersectingSources() extends Throwable

  // Provide effects that return both expression and params, get back a tile
  def apply[F[_], Par[_], Param](
    getExpression: F[Expression],
    getParams: F[Map[String, Param]],
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ): F[Interpreted[NEL[Histogram[Double]]]] =
    for {
      params            <- getParams
      rasterExtents     <- NEL.fromListUnsafe(params.values.toList)
                             .map(_.rasterExtents)
                             .parSequence
                             .map(_.flatten)
      intersection      <- F.delay { SampleUtils.intersectExtents(rasterExtents.map(_.extent))
                                  .getOrElse(throw new RequireIntersectingSources()) }
      _                 <- F.delay { logger.trace(s"[LayerHistogram] Intersection of provided layer extents calculated: $intersection") }
      cellSize          <- F.delay { SampleUtils.chooseLargestCellSize(rasterExtents.map(_.cellSize)) }
      _                 <- F.delay { logger.trace(s"[LayerHistogram] Largest cell size of provided layers calculated: $cellSize") }
      mbtileForExtent   <- F.delay { LayerExtent(getExpression, getParams, interpreter) }
      _                 <- F.delay { logger.trace(s"[LayerHistogram] calculating histogram from (approximately) ${intersection.area / (cellSize.width * cellSize.height)} cells") }
      interpretedTile   <- mbtileForExtent(intersection, cellSize)
    } yield {
      interpretedTile.map { mbtile =>
        NEL.fromListUnsafe(mbtile.bands.map { band  => StreamingHistogram.fromTile(band) }.toList)
      }
    }

  def generateExpression[F[_], Par[_], Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: F[Map[String, Param]],
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ) = apply[F, Par, Param](getParams.map(mkExpr(_)), getParams, interpreter, maxCells)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[F[_], Par[_], Param](
    expr: Expression,
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ): (Map[String, Param]) => F[Interpreted[NEL[Histogram[Double]]]] =
    (paramMap: Map[String, Param]) => {
      apply[F, Par, Param](F.pure(expr), F.pure(paramMap), interpreter, maxCells)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[F[_], Par[_], Param](
    param: Param,
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ) = {
    val eval = curried[F, Par, Param](RasterVar("identity"), BufferingInterpreter.DEFAULT, maxCells)
    eval(Map("identity" -> param))
  }

}

