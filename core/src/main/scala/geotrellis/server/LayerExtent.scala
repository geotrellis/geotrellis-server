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

import ExtentReification.ops._

import geotrellis.vector.Extent
import geotrellis.raster._
import com.azavea.maml.util.Vars
import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.eval._

import cats.data.Validated._
import cats.effect._
import cats.implicits._

object LayerExtent {
  val logger = org.log4s.getLogger

  // Provide IOs for both expression and params, get back a tile
  def apply[Param](
    getExpression: IO[Expression],
    getParams: IO[Map[String, Param]],
    interpreter: Interpreter[IO]
  )(
    implicit reify: ExtentReification[Param],
             contextShift: ContextShift[IO]
  ): (Extent, CellSize) => IO[Interpreted[MultibandTile]]  = (extent: Extent, cs: CellSize) =>  {
    for {
      expr             <- getExpression
      _                <- IO { logger.trace(s"[LayerExtent] Retrieved MAML AST for extent ($extent) and cellsize ($cs): ${expr.toString}") }
      paramMap         <- getParams
      _                <- IO { logger.trace(s"[LayerExtent] Retrieved parameters for extent ($extent) and cellsize ($cs): ${paramMap.toString}") }
      vars             <- IO { Vars.varsWithBuffer(expr) }
      params           <- vars.toList.parTraverse { case (varName, (_, buffer)) =>
        val thingify = paramMap(varName).extentReification
        thingify(extent, cs).map(varName -> _)
      } map { _.toMap }
      reified          <- Expression.bindParams(expr, params.mapValues(RasterLit(_))) match {
        case Valid(expression) => interpreter(expression)
        case Invalid(errors) => throw new Exception(errors.map(_.repr).reduce)
      }
    } yield
    reified
      .andThen(_.as[MultibandTile])
      .map {
        _.crop(RasterExtent(extent, cs)
          .gridBoundsFor(extent))
      }
  }

  def generateExpression[Param](
    mkExpr: Map[String, Param] => Expression,
    getParams: IO[Map[String, Param]],
    interpreter: Interpreter[IO]
  )(
    implicit reify: ExtentReification[Param],
             contextShift: ContextShift[IO]
  ) = apply[Param](getParams.map(mkExpr(_)), getParams, interpreter)


  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[Param](
    expr: Expression,
    interpreter: Interpreter[IO]
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
      val eval = curried(RasterVar("identity"), ConcurrentInterpreter.DEFAULT)
      eval(Map("identity" -> param), extent, cellsize)
    }
}
