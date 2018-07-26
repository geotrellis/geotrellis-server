package geotrellis.server.core.maml

import MamlTmsReification.ops._

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

object MamlTms extends LazyLogging {

  // Provide IOs for both expression and params, get back a tile
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: MamlTmsReification[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ) = (z: Int, x: Int, y: Int) => {
    for {
      expr             <- getExpression
      _                <- IO.pure(logger.info(s"Retrieved MAML AST at TMS ($z, $x, $y): ${expr.asJson.noSpaces}"))
      paramMap         <- getParams
      _                <- IO.pure(logger.info(s"Retrieved parameters for TMS ($z, $x, $y): ${paramMap.asJson.noSpaces}"))
      vars             <- IO.pure { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                            paramMap(varName).tmsReification(buffer)(t)(z, x, y).map(varName -> _)
                          } map { _.toMap }
      reified          <- IO.pure { Expression.bindParams(expr, params) }
    } yield reified.andThen(interpreter(_)).andThen(_.as[Tile])
  }


  /** Provide a function to produce an expression given a set of arguments and an
    *  IO for getting arguments; getting back a tile
    */
  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: MamlTmsReification[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter)(reify, enc, t)


  /** Provide an expression and expect arguments to fulfill its needs */
  def fromExpression[Param](
    expr: Expression,
    interpreter: BufferingInterpreter
  )(
    implicit reify: MamlTmsReification[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ): (Map[String, Param], Int, Int, Int) => IO[Interpreted[Tile]] =
    (paramMap: Map[String, Param], z: Int, x: Int, y: Int) => {
      apply[Param](IO.pure(expr), IO.pure(paramMap), interpreter)(reify, enc, t)(z, x, y)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[Param](
    interpreter: BufferingInterpreter
  )(
    implicit reify: MamlTmsReification[Param],
             enc: Encoder[Param],
             t: Timer[IO]
  ) = fromExpression(RasterVar("self"), interpreter)

}

