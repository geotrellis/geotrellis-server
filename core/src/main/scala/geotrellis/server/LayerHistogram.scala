package geotrellis.server

import ExtentReification.ops._
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

import scala.util.Random

object LayerHistogram extends LazyLogging {

  /** Sample imagery based on a provided sample extent */
  final def sampleRasterExtent(uberExtent: Extent, cs: CellSize, maxCells: Int): Extent = {
    if (uberExtent.width <= 0 || uberExtent.height <= 0) uberExtent
    else {
      logger.debug(s"Finding sample extent for UberExtent $uberExtent for $cs with a maximum sample of $maxCells cells")
      val sampleWidth = math.sqrt(maxCells.toDouble) * cs.width
      val sampleHeight = math.sqrt(maxCells.toDouble) * cs.height

      logger.debug(s"orig height: ${uberExtent.height}, width: ${uberExtent.width}")
      logger.debug(s"ideal sample height: $sampleHeight, ideal sample width: $sampleWidth")
      // Sanity check here - if the desired sample extent is larger than the source extent, just use the source extent
      val newWidth = if (sampleWidth > uberExtent.width) uberExtent.width else sampleWidth
      val newHeight = if (sampleHeight > uberExtent.height) uberExtent.height else sampleHeight

      // we need to avoid doing math which *should* result in equal minimum values (floating point issues)
      val xmin =
        if (newWidth == uberExtent.width) uberExtent.xmin
        else uberExtent.xmax - newWidth
      val ymin =
        if (newHeight == uberExtent.height) uberExtent.ymin
        else uberExtent.ymax - newHeight

      val sample = Extent(xmin, ymin, uberExtent.xmax, uberExtent.ymax)
      logger.debug(s"The sample extent covers ${(sample.area / uberExtent.area) * 100}% of the source extent")
      sample
    }
  }


  /** Choose the largest cellsize */
  final def chooseLargestCellSize(nativeCellSizes: NEL[CellSize]): CellSize =
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
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             enc: Encoder[Param],
             contextShift: ContextShift[IO]
  ): IO[Interpreted[List[Histogram[Double]]]] =
    for {
      params           <- getParams
      rasterExtents    <- NEL.fromListUnsafe(params.values.toList)
                            .map(_.rasterExtents)
                            .parSequence
                            .map(_.flatten)
      intersection     <- IO { rasterExtents.foldLeft(Option.empty[Extent])({ (mbExtent, re) =>
                            mbExtent match {
                              case Some(extent) =>
                                extent.intersection(re.extent)
                              case None =>
                                Some(re.extent)
                            }
                          }).getOrElse(throw new RequireIntersectingSources()) }
      cellSize         <- IO { chooseLargestCellSize(rasterExtents.map(_.cellSize)) }
      sampleExtent     <- IO { sampleRasterExtent(intersection, cellSize, maxCells) }
      mbtileForExtent  <- IO { LayerExtent(getExpression, getParams, interpreter) }
      interpretedTile  <- mbtileForExtent(sampleExtent, cellSize)
    } yield interpretedTile.map { mbtile =>
      mbtile.bands.map { band => StreamingHistogram.fromTile(band) }.toList
    }

  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: ExtentReification[Param],
             extended: HasRasterExtents[Param],
             enc: Encoder[Param],
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
             enc: Encoder[Param],
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
             enc: Encoder[Param],
             contextShift: ContextShift[IO]
  ) = {
    val eval = curried(RasterVar("identity"), BufferingInterpreter.DEFAULT, maxCells)
    eval(Map("identity" -> param))
  }

}

