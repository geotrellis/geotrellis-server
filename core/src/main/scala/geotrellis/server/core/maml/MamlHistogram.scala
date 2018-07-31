package geotrellis.server.core.maml

import metadata._
import MamlExtension.ops._
import SummaryZoom.ops._

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
import cats.syntax.all._
import geotrellis.vector.Extent
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.Tile


object MamlHistogram extends LazyLogging {

  // Provide IOs for both expression and params, get back a tile
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter,
    maxCells: Int
  )(
    implicit reify: MamlExtentReification[Param],
             extended: MamlExtension[Param],
             summary: SummaryZoom[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ): IO[Interpreted[Histogram[Double]]] =
    for {
      params           <- getParams
      extentsCellSizes <- NEL.fromList(params.values.toList).getOrElse(throw new NoSuchElementException("No arguments provided"))
                               .parTraverse { _.maxAcceptableCellsize(maxCells)  }: IO[NEL[(Extent, CellSize)]]
      extentCellSize   <- IO { extentsCellSizes.tail.foldLeft(extentsCellSizes.head)({ case ((extent1, cs1), (extent2, cs2)) =>
                            val newExtent = (extent1 combine extent2)
                            val newCs = (CellSize(math.max(cs1.width, cs2.width), math.max(cs1.height, cs2.height)))
                            (newExtent, newCs)
                          }) }
      extent = extentCellSize._1
      cellsize = extentCellSize._2
      tileForExtent    <- IO { MamlExtent(getExpression, getParams, interpreter) }
      interpretedTile  <- tileForExtent(extent)
    } yield interpretedTile.map(StreamingHistogram.fromTile(_))
}

