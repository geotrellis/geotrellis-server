package geotrellis.server

import TmsReification.ops._

import com.azavea.maml.util.Vars
import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.ast.codec.tree._
import com.azavea.maml.eval._
import com.typesafe.scalalogging.LazyLogging
import cats._
import cats.effect._
import cats.implicits._
import geotrellis.raster._

/** Provides methods for producing TMS tiles */
object LayerTms extends LazyLogging {

  /**
   * Given an [[Expression]], a parameter map, and an interpreter, create a function
   *  which takes z, x, and y coordinates and returns the corresponding tile.
   *
   * @tparam Param a type whose instances can refer to layers
   * @param getExpression an [[F]] yielding a description of the map algebra
   *                      to be carried out
   * @param getParams an [[F]] yielding a map from source node ID to some stand-in
   *                  for a tile source
   * @param interpreter a MAML-compliant interpreter (with buffering)
   * @return a function from (Int, Int, Int) to a Tile corresponding to the Param provided
   */
  def apply[F[_], Par[_], Param](
    getExpression: F[Expression],
    getParams: F[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: TmsReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ): (Int, Int, Int) => F[Interpreted[MultibandTile]] = (z: Int, x: Int, y: Int) => {
    for {
      expr             <- getExpression
      _                <- F.pure(logger.info(s"Retrieved MAML AST at TMS ($z, $x, $y): ${expr.toString}"))
      paramMap         <- getParams
      _                <- F.pure(logger.info(s"Retrieved parameters for TMS ($z, $x, $y): ${paramMap.toString}"))
      vars             <- F.delay { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                            val eval = paramMap(varName).tmsReification(buffer)
                            eval(z, x, y).map(varName -> _)
                          } map { _.toMap }
      reified          <- F.pure { Expression.bindParams(expr, params) }
    } yield reified.andThen(interpreter(_)).andThen(_.as[MultibandTile])
  }


  /** Provide a function to produce an expression given a set of arguments and an
    *  [[F]] for getting arguments; getting back a tile
    */
  def generateExpression[F[_], Par[_], Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: F[Map[String, Param]],
    interpreter: BufferingInterpreter
  )(
    implicit reify: TmsReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ) = apply[F, Par, Param](getParams.map(mkExpr(_)), getParams, interpreter)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[F[_], Par[_], Param](
    expr: Expression,
    interpreter: BufferingInterpreter
  )(
    implicit reify: TmsReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ): (Map[String, Param], Int, Int, Int) => F[Interpreted[MultibandTile]] =
    (paramMap: Map[String, Param], z: Int, x: Int, y: Int) => {
      val eval = apply[F, Par, Param](F.pure(expr), F.pure(paramMap), interpreter)
      eval(z, x, y)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[F[_], Par[_], Param](
    param: Param
  )(
    implicit reify: TmsReification[Param],
             F: ConcurrentEffect[F],
             P: Parallel[F, Par]
  ) = (z: Int, x: Int, y: Int) => {
    val eval = curried[F, Par, Param](RasterVar("identity"), BufferingInterpreter.DEFAULT)
    eval(Map("identity" -> param), z, x, y)
  }

}

