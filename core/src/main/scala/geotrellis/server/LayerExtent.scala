package geotrellis.server

import ExtentReification.ops._

import geotrellis.raster._
import geotrellis.vector.Extent
import com.azavea.maml.util.Vars
import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import com.typesafe.scalalogging.LazyLogging
import cats._
import cats.effect._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}


object LayerExtent extends LazyLogging {

  // Provide IOs for both expression and params, get back a tile
  def apply[F[_], Par[_], Param](
    getExpression: F[Expression],
    getParams: F[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: ExtentReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ): (Extent, CellSize) => F[Interpreted[MultibandTile]]  = (extent: Extent, cs: CellSize) =>  {
    for {
      expr             <- getExpression
      _                <- F.delay { logger.trace(s"[LayerExtent] Retrieved MAML AST for extent ($extent) and cellsize ($cs): ${expr.toString}") }
      paramMap         <- getParams
      _                <- F.delay { logger.trace(s"[LayerExtent] Retrieved parameters for extent ($extent) and cellsize ($cs): ${paramMap.toString}") }
      vars             <- F.delay { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                            val thingify = paramMap(varName).extentReification
                            thingify(extent, cs).map(varName -> _)
                          } map { _.toMap }
      reified          <- F.delay { Expression.bindParams(expr, params) }
    } yield reified.andThen(interpreter(_)).andThen(_.as[MultibandTile])
  }

  def generateExpression[F[_], Par[_], Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: F[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: ExtentReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ) = apply[F, Par, Param](getParams.map(mkExpr(_)), getParams, interpreter)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[F[_], Par[_], Param](
    expr: Expression,
    interpreter: BufferingInterpreter
  )(
    implicit reify: ExtentReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ): (Map[String, Param], Extent, CellSize) => F[Interpreted[MultibandTile]] =
    (paramMap: Map[String, Param], extent: Extent, cellsize: CellSize) => {
      val eval = apply[F, Par, Param](F.pure(expr), F.pure(paramMap), interpreter)
      eval(extent, cellsize)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[F[_], Par[_], Param](
    param: Param
  )(
    implicit reify: ExtentReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ): (Extent, CellSize) => F[Interpreted[MultibandTile]] =
    (extent: Extent, cellsize: CellSize) => {
      val eval = curried[F, Par, Param](RasterVar("identity"), BufferingInterpreter.DEFAULT)
      eval(Map("identity" -> param), extent, cellsize)
    }
}

