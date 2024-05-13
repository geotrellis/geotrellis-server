/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server

import com.azavea.maml.util.Vars
import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.eval._
import cats._
import cats.effect._
import cats.implicits._
import cats.data.Validated._
import geotrellis.raster.{io => _, _}
import org.typelevel.log4cats.Logger

/**
 * Provides methods for producing TMS tiles
 */
object LayerTms {

  /**
   * Given an [[Expression]], a parameter map, and an interpreter, create a function which takes z, x, and y coordinates and returns the corresponding
   * tile.
   *
   * @tparam T
   *   a type whose instances can refer to layers
   * @param getExpression
   *   an [[F]] yielding a description of the map algebra to be carried out
   * @param getParams
   *   an [[F]] yielding a map from source node ID to some stand-in for a tile source
   * @param interpreter
   *   a MAML-compliant interpreter (with buffering)
   * @return
   *   a function from (Int, Int, Int) to a Tile corresponding to the Param provided
   */
  def apply[F[_]: Logger: Parallel: Monad, T: TmsReification[F, *]](
    getExpression: F[Expression],
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F],
    cellType: Option[CellType]
  ): (Int, Int, Int) => F[Interpreted[MultibandTile]] = { (z: Int, x: Int, y: Int) =>
    for {
      expr <- getExpression
      logger = Logger[F]
      _ <- logger.trace(
        s"Retrieved MAML AST at TMS ($z, $x, $y): ${expr.toString}"
      )
      paramMap <- getParams
      _ <- logger.trace(
        s"Retrieved parameters for TMS ($z, $x, $y): ${paramMap.toString}"
      )
      vars = Vars.varsWithBuffer(expr)
      params <- vars.toList
        .parTraverse { case (varName, (_, buffer)) =>
          val eval =
            implicitly[TmsReification[F, T]]
              .tmsReification(paramMap(varName), buffer)
          eval(z, x, y).map(varName -> _)
        }
        .map { _.toMap }
      reified <- Expression.bindParams(expr, params.map { case (key, value) => key -> RasterLit(value) }) match {
        case Valid(expression) => interpreter(expression)
        case Invalid(errors)   => throw new Exception(errors.map(_.repr).reduce)
      }
    } yield reified.andThen(_.as[MultibandTile]).andThen(res => Valid(cellType.fold(res)(res.interpretAs)))
  }

  /**
   * Provide a function to produce an expression given a set of arguments and an F for getting arguments; getting back a tile
   */
  def generateExpression[F[_]: Logger: Parallel: Monad, T: TmsReification[F, *]](
    mkExpr: Map[String, T] => Expression,
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F]
  ): (Int, Int, Int) => F[Interpreted[MultibandTile]] = apply[F, T](getParams.map(mkExpr(_)), getParams, interpreter, None)

  /**
   * Provide an expression and expect arguments to fulfill its needs
   */
  def curried[F[_]: Logger: Parallel: Monad, T: TmsReification[F, *]](
    expr: Expression,
    interpreter: Interpreter[F],
    cellType: Option[CellType]
  ): (Map[String, T], Int, Int, Int) => F[Interpreted[MultibandTile]] =
    (paramMap: Map[String, T], z: Int, x: Int, y: Int) => {
      val eval = apply[F, T](expr.pure[F], paramMap.pure[F], interpreter, cellType)
      eval(z, x, y)
    }

  /**
   * The identity endpoint (for simple display of raster)
   */
  def concurrent[F[_]: Logger: Parallel: Monad: Concurrent, T: TmsReification[F, *]](
    getExpression: F[Expression],
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F]
  ): (Int, Int, Int) => F[Interpreted[MultibandTile]] =
    apply(getExpression, getParams, interpreter, None)

  /**
   * The identity endpoint (for simple display of raster)
   */
  def withCellType[F[_]: Logger: Parallel: Monad: Concurrent, T: TmsReification[F, *]](
    param: T,
    cellType: CellType
  ): (Int, Int, Int) => F[Interpreted[MultibandTile]] =
    (z: Int, x: Int, y: Int) => {
      val eval = curried[F, T](RasterVar("identity"), ConcurrentInterpreter.DEFAULT, cellType.some)
      eval(Map("identity" -> param), z, x, y)
    }

  def identity[F[_]: Logger: Parallel: Monad: Concurrent, T: TmsReification[F, *]](
    param: T
  ): (Int, Int, Int) => F[Interpreted[MultibandTile]] =
    (z: Int, x: Int, y: Int) => {
      val eval = curried[F, T](RasterVar("identity"), ConcurrentInterpreter.DEFAULT, None)
      eval(Map("identity" -> param), z, x, y)
    }
}
