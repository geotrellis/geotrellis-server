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
import cats.implicits._
import geotrellis.raster._
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
  ) =
    for {
      params           <- getParams
      extentsAndCS     <- params.values.map { _.maxAcceptableCellsize(maxCells)  } // iterable of (extent, cs)... need to merge
      (extent, cs) = extentsAndCS.reduce { case ((extent1, cs1), (extent2, cs2)) =>
                       val newExtent = (extent1 merge extent2)
                       val newCs = (CellSize(math.max(cs1.width, cs2.width), math.max(cs1.height, cs2.height)))
                       (newExtent, newCs)
                     }
      validatedTile    <- MamlExtent(getExpression, getParams, interpreter)
      histogram        <- IO { validatedTile(extent).histogram }
    } yield histogram

}
