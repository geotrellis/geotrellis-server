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
    interpreter: BufferingInterpreter,
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
      cellSize          <- IO { SampleUtils.chooseLargestCellSize(rasterExtents.map(_.cellSize)) }
      sampleExtents     <- IO { SampleUtils.sampleRasterExtent(intersection, cellSize, maxCells) }
      mbtileForExtent   <- IO { LayerExtent(getExpression, getParams, interpreter) }
      interpretedTileTL <- mbtileForExtent(sampleExtents._1, cellSize)
      interpretedTileTR <- mbtileForExtent(sampleExtents._2, cellSize)
      interpretedTileBL <- mbtileForExtent(sampleExtents._3, cellSize)
      interpretedTileBR <- mbtileForExtent(sampleExtents._4, cellSize)
    } yield (interpretedTileTL, interpretedTileTR, interpretedTileBL, interpretedTileBR).mapN {
      case (mbtileTL, mbtileTR, mbtileBL, mbtileBR) =>
        val tl = mbtileTL.bands.map { band => StreamingHistogram.fromTile(band) }.toList
        val tr = mbtileTR.bands.map { band => StreamingHistogram.fromTile(band) }.toList
        val bl = mbtileBL.bands.map { band => StreamingHistogram.fromTile(band) }.toList
        val br = mbtileBR.bands.map { band => StreamingHistogram.fromTile(band) }.toList
        List(tr, bl, br).foldLeft(tl.asInstanceOf[List[Histogram[Double]]])({ (histListAcc, histListNext) =>
          val arr = mutable.ListBuffer.empty[Histogram[Double]]
          for (idx <- 0 to histListAcc.length - 1) {
            arr += (histListAcc(idx) merge histListNext(idx).asInstanceOf[Histogram[Double]])
          }
          arr.toList
        })
    }

  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             contextShift: ContextShift[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter, maxCells)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[Param](
    expr: Expression,
    interpreter: BufferingInterpreter,
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
    val eval = curried(RasterVar("identity"), BufferingInterpreter.DEFAULT, maxCells)
    eval(Map("identity" -> param))
  }

}

