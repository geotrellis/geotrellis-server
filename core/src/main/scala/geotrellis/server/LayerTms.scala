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
import cats.data.Validated._
import cats.data.{NonEmptyList => NEL}
import geotrellis.raster._

/** Provides methods for producing TMS tiles */
object LayerTms extends LazyLogging {

  /**
   * Given an [[Expression]], a parameter map, and an interpreter, create a function
   *  which takes z, x, and y coordinates and returns the corresponding tile.
   *
   * @tparam Param a type whose instances can refer to layers
   * @param getExpression an [[IO]] yielding a description of the map algebra
   *                      to be carried out
   * @param getParams an [[IO]] yielding a map from source node ID to some stand-in
   *                  for a tile source
   * @param interpreter a MAML-compliant interpreter (with buffering)
   * @return a function from (Int, Int, Int) to a Tile corresponding to the Param provided
   */
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: Interpreter[IO]
  )(
    implicit reify: TmsReification[Param],
             contextShift: ContextShift[IO]
  ): (Int, Int, Int) => IO[Interpreted[MultibandTile]] = (z: Int, x: Int, y: Int) => {
    for {
      expr             <- getExpression
      _                <- IO { logger.info(s"Retrieved MAML AST at TMS ($z, $x, $y): ${expr.toString}") }
      paramMap         <- getParams
      _                <- IO { logger.info(s"Retrieved parameters for TMS ($z, $x, $y): ${paramMap.toString}") }
      vars             <- IO { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
                            val eval = paramMap(varName).tmsReification(buffer)
                            eval(z, x, y).map(varName -> _)
                          } map { _.toMap }
      reified          <- Expression.bindParams(expr, params.mapValues(RasterLit(_))) match {
        case Valid(expression) => interpreter(expression)
        case Invalid(errors) => throw new Exception(errors.map(_.repr).reduce)
      }
    } yield reified.andThen(_.as[MultibandTile])
  }


  /** Provide a function to produce an expression given a set of arguments and an
    *  IO for getting arguments; getting back a tile
    */
  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: Interpreter[IO]
  )(
    implicit reify: TmsReification[Param],
             contextShift: ContextShift[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[Param](
    expr: Expression,
    interpreter: Interpreter[IO]
  )(
    implicit reify: TmsReification[Param],
             contextShift: ContextShift[IO]
  ): (Map[String, Param], Int, Int, Int) => IO[Interpreted[MultibandTile]] =
    (paramMap: Map[String, Param], z: Int, x: Int, y: Int) => {
      val eval = apply[Param](IO.pure(expr), IO.pure(paramMap), interpreter)
      eval(z, x, y)
    }


  /** The identity endpoint (for simple display of raster) */
  def identity[Param](
    param: Param
  )(
    implicit reify: TmsReification[Param],
             contextShift: ContextShift[IO]
  ) = (z: Int, x: Int, y: Int) => {
    val eval = curried(RasterVar("identity"), ConcurrentInterpreter.DEFAULT)
    eval(Map("identity" -> param), z, x, y)
  }

}
