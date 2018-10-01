package geotrellis.server.core.maml

import geotrellis.server.core.maml.reification._
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
             contextShift: ContextShift[IO]
  ): (Extent, CellSize) => IO[Interpreted[Tile]]  = (extent: Extent, cs: CellSize) =>  {
    for {
      expr             <- getExpression
      _                <- IO.pure(logger.info(s"Retrieved MAML AST for extent ($extent) and cellsize ($cs): ${expr.asJson.noSpaces}"))
      paramMap         <- getParams
      _                <- IO.pure(logger.info(s"Retrieved parameters for extent ($extent) and cellsize ($cs): ${paramMap.asJson.noSpaces}"))
      vars             <- IO.pure { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.traverse { case (varName, (_, buffer)) =>
                            paramMap(varName).extentReification(contextShift)(extent, cs).map(varName -> _)
                          } map { _.toMap }
      reified          <- IO.pure { Expression.bindParams(expr, params) }
    } yield reified.andThen(interpreter(_)).andThen(_.as[Tile])
  }
}

