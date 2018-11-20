package geotrellis.server

import ExtentReification.ops._

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


object LayerExtent extends LazyLogging {

  // Provide IOs for both expression and params, get back a tile
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: ExtentReification[Param],
             enc: Encoder[Param],
             contextShift: ContextShift[IO]
  ): (Extent, CellSize) => IO[Interpreted[MultibandTile]]  = (extent: Extent, cs: CellSize) =>  {
    for {
      expr             <- getExpression
      _                <- IO.pure(logger.info(s"Retrieved MAML AST for extent ($extent) and cellsize ($cs): ${expr.asJson.noSpaces}"))
      paramMap         <- getParams
      _                <- IO.pure(logger.info(s"Retrieved parameters for extent ($extent) and cellsize ($cs): ${paramMap.asJson.noSpaces}"))
      vars             <- IO.pure { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                            val thingify = paramMap(varName).extentReification
                            thingify(extent, cs).map(varName -> _)
                          } map { _.toMap }
      reified          <- IO.pure { Expression.bindParams(expr, params) }
    } yield reified.andThen(interpreter(_)).andThen(_.as[MultibandTile])
  }

  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: ExtentReification[Param],
             enc: Encoder[Param],
             contextShift: ContextShift[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[Param](
    expr: Expression,
    interpreter: BufferingInterpreter
  )(
    implicit reify: ExtentReification[Param],
             enc: Encoder[Param],
             contextShift: ContextShift[IO]
  ): (Map[String, Param], Extent, CellSize) => IO[Interpreted[MultibandTile]] =
    (paramMap: Map[String, Param], extent: Extent, cellsize: CellSize) => {
      val eval = apply[Param](IO.pure(expr), IO.pure(paramMap), interpreter)
      eval(extent, cellsize)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[Param](
    param: Param
  )(
    implicit reify: ExtentReification[Param],
             enc: Encoder[Param],
             contextShift: ContextShift[IO]
  ): (Extent, CellSize) => IO[Interpreted[MultibandTile]] =
    (extent: Extent, cellsize: CellSize) => {
      val eval = curried(RasterVar("identity"), BufferingInterpreter.DEFAULT)
      eval(Map("identity" -> param), extent, cellsize)
    }
}

