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
    interpreter: Interpreter
  )(
    implicit reify: ExtentReification[Param],
             contextShift: ContextShift[IO]
  ): (Extent, CellSize) => IO[Interpreted[MultibandTile]]  = (extent: Extent, cs: CellSize) =>  {
    for {
      expr             <- getExpression
      _                <- IO { logger.trace(s"Retrieved MAML AST for extent ($extent) and cellsize ($cs): ${expr.toString}") }
      paramMap         <- getParams
      _                <- IO { logger.trace(s"Retrieved parameters for extent ($extent) and cellsize ($cs): ${paramMap.toString}") }
      vars             <- IO { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                            val thingify = paramMap(varName).extentReification
                            val bufferedExtent = Extent(
                              extent.xmin - cs.height * buffer,
                              extent.ymin - cs.width * buffer,
                              extent.xmax + cs.height * buffer,
                              extent.ymax + cs.width * buffer
                            )
                            thingify(bufferedExtent, cs).map(varName -> _)
                          } map { _.toMap }
      reified          <- IO { Expression.bindParams(expr, params.mapValues(RasterLit(_))) }
    } yield reified
      .andThen(interpreter(_))
      .andThen(_.as[MultibandTile])
      .map {
        _.crop(RasterExtent(extent, cs)
          .gridBoundsFor(extent))
      }
  }

  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: Interpreter
  )(
    implicit reify: ExtentReification[Param],
             contextShift: ContextShift[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[Param](
    expr: Expression,
    interpreter: Interpreter
  )(
    implicit reify: ExtentReification[Param],
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
             contextShift: ContextShift[IO]
  ): (Extent, CellSize) => IO[Interpreted[MultibandTile]] =
    (extent: Extent, cellsize: CellSize) => {
      val eval = curried(RasterVar("identity"), Interpreter.DEFAULT)
      eval(Map("identity" -> param), extent, cellsize)
    }
}

