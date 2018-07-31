package geotrellis.server.core.maml

import MamlExtentReification.ops._

import geotrellis.vector.Extent
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

object MamlExtent extends LazyLogging {

  // Provide IOs for both expression and params, get back a tile
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: MamlExtentReification[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ): Extent => IO[Interpreted[Tile]]  = (extent: Extent) =>  {
    ???
  }
}
