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

import geotrellis.vector.Extent
import geotrellis.raster.{io => _, _}
import com.azavea.maml.util.Vars
import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.eval._

import cats._
import cats.data.Validated._
import cats.effect._
import cats.Parallel
import cats.implicits._
import org.typelevel.log4cats.Logger

object LayerExtent {
  def apply[F[_]: Logger: Parallel: Monad, T: ExtentReification[F, *]](
    getExpression: F[Expression],
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F],
    cellType: Option[CellType]
  ): (Extent, Option[CellSize]) => F[Interpreted[MultibandTile]] = {
    val logger = Logger[F]
    (extent: Extent, cellSize: Option[CellSize]) =>
      for {
        expr <- getExpression
        _ <- logger.trace(
          s"[LayerExtent] Retrieved MAML AST for extent ($extent) and cellsize ($cellSize): ${expr.toString}"
        )
        paramMap <- getParams
        _ <- logger.trace(
          s"[LayerExtent] Retrieved Teters for extent ($extent) and cellsize ($cellSize): ${paramMap.toString}"
        )
        vars = Vars.varsWithBuffer(expr)
        params <- vars.toList
          .parTraverse { case (varName, (_, buffer)) =>
            val thingify = implicitly[ExtentReification[F, T]].extentReification(paramMap(varName))
            thingify(extent, cellSize).map(varName -> _)
          }
          .map { _.toMap }
        reified <- Expression.bindParams(expr, params.map { case (key, value) => key -> RasterLit(value) }) match {
          case Valid(expression) => interpreter(expression)
          case Invalid(errors)   => throw new Exception(errors.map(_.repr).reduce)
        }
      } yield reified
        .andThen(_.as[MultibandTile])
        .andThen(res => Valid(cellType.fold(res)(res.interpretAs)))
        .map(tile => cellSize.fold(tile)(cs => tile.crop(RasterExtent(extent, cs).gridBoundsFor(extent))))
  }

  def generateExpression[F[_]: Logger: Parallel: Monad, T: ExtentReification[F, *]](
    mkExpr: Map[String, T] => Expression,
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F]
  ): (Extent, Option[CellSize]) => F[Interpreted[MultibandTile]] = apply[F, T](getParams.map(mkExpr(_)), getParams, interpreter, None)

  /**
   * Provide an expression and expect arguments to fulfill its needs
   */
  def curried[F[_]: Logger: Parallel: Monad, T: ExtentReification[F, *]](
    expr: Expression,
    interpreter: Interpreter[F],
    cellType: Option[CellType]
  ): (Map[String, T], Extent, Option[CellSize]) => F[Interpreted[MultibandTile]] =
    (paramMap: Map[String, T], extent: Extent, cellsize: Option[CellSize]) => {
      val eval =
        apply[F, T](expr.pure[F], paramMap.pure[F], interpreter, cellType)
      eval(extent, cellsize)
    }

  def concurrent[F[_]: Logger: Parallel: Monad: Concurrent, T: ExtentReification[F, *]](
    getExpression: F[Expression],
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F]
  ): (Extent, Option[CellSize]) => F[Interpreted[MultibandTile]] =
    apply(getExpression, getParams, interpreter, None)

  /**
   * The identity endpoint (for simple display of raster)
   */
  def withCellType[F[_]: Logger: Parallel: Monad: Concurrent, T: ExtentReification[F, *]](
    param: T,
    cellType: CellType
  ): (Extent, Option[CellSize]) => F[Interpreted[MultibandTile]] =
    (extent: Extent, cellsize: Option[CellSize]) => {
      val eval = curried(RasterVar("identity"), ConcurrentInterpreter.DEFAULT, cellType.some)
      eval(Map("identity" -> param), extent, cellsize)
    }

  def identity[F[_]: Logger: Parallel: Monad: Concurrent, T: ExtentReification[F, *]](
    param: T
  ): (Extent, Option[CellSize]) => F[Interpreted[MultibandTile]] =
    (extent: Extent, cellsize: Option[CellSize]) => {
      val eval = curried(RasterVar("identity"), ConcurrentInterpreter.DEFAULT, None)
      eval(Map("identity" -> param), extent, cellsize)
    }
}
