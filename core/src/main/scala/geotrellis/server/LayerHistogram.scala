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

import geotrellis.server.extent.SampleUtils

import com.azavea.maml.error._
import com.azavea.maml.ast._
import com.azavea.maml.eval._
import geotrellis.vector.Extent
import geotrellis.raster.{io => _, _}

import cats._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.syntax.option._
import cats.instances.option._
import io.chrisdavenport.log4cats.Logger

object LayerHistogram {
  case class NoSuitableHistogramResolution(cells: Int) extends Throwable
  case class RequireIntersectingSources()              extends Throwable

  // Added so that we can get combine
  implicit val extentSemigroup: Semigroup[Extent] = { _ combine _ }

  // Provide IOs for both expression and params, get back a tile
  def apply[F[_]: Logger: Parallel: Monad, T: ExtentReification[F, *]: HasRasterExtents[F, *]](
    getExpression: F[Expression],
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F],
    maxCells: Int
  ): F[Interpreted[List[Histogram[Double]]]] = {
    val logger = Logger[F]
    for {
      params <- getParams
      rasterExtents <- NEL
        .fromListUnsafe(params.values.toList)
        .traverse(HasRasterExtents[F, T].rasterExtents(_))
        .map(_.flatten)
      extents <- NEL
        .fromListUnsafe(params.values.toList)
        .traverse(
          HasRasterExtents[F, T]
            .rasterExtents(_)
            .map(z => z.map(_.extent).reduce)
        )
      intersectionO = SampleUtils.intersectExtents(extents)
      _ <- intersectionO traverse { intersection =>
        logger.trace(
          s"[LayerHistogram] Intersection of provided layer extents calculated: $intersection"
        )
      }
      cellSize = SampleUtils.chooseLargestCellSize(rasterExtents, maxCells)
      _ <- logger.trace(
        s"[LayerHistogram] Largest cell size of provided layers calculated: $cellSize"
      )
      mbtileForExtent = LayerExtent(getExpression, getParams, interpreter, None)
      _ <- intersectionO traverse { intersection =>
        logger.trace(
          s"[LayerHistogram] calculating histogram from (approximately) ${intersection.area / (cellSize.width * cellSize.height)} cells"
        )
      }
      interpretedTile <- intersectionO traverse { intersection =>
        mbtileForExtent(intersection, cellSize.some)
      }
    } yield interpretedTile.map { mbtileValidated =>
      mbtileValidated.map { mbTile =>
        mbTile.bands.map { band =>
          StreamingHistogram.fromTile(band)
        }.toList
      }
    } getOrElse ???
  }

  def generateExpression[F[_]: Logger: Parallel: Monad, T: ExtentReification[F, *]: HasRasterExtents[F, *]](
    mkExpr: Map[String, T] => Expression,
    getParams: F[Map[String, T]],
    interpreter: Interpreter[F],
    maxCells: Int
  ): F[Interpreted[List[Histogram[Double]]]] =
    apply[F, T](getParams.map(mkExpr(_)), getParams, interpreter, maxCells)

  /** Provide an expression and expect arguments to fulfill its needs */
  def curried[F[_]: Logger: Parallel: Monad, T: ExtentReification[F, *]: HasRasterExtents[F, *]](
    expr: Expression,
    interpreter: Interpreter[F],
    maxCells: Int
  ): Map[String, T] => F[Interpreted[List[Histogram[Double]]]] =
    (paramMap: Map[String, T]) =>
      apply[F, T](
        expr.pure[F],
        paramMap.pure[F],
        interpreter,
        maxCells
      )

  /** The identity endpoint (for simple display of raster) */
  def concurrent[F[_]: Logger: Parallel: Monad: Concurrent, T: ExtentReification[F, *]: HasRasterExtents[F, *]](
    param: T,
    maxCells: Int
  ): F[Interpreted[List[Histogram[Double]]]] = {
    val eval =
      curried[F, T](
        RasterVar("identity"),
        ConcurrentInterpreter.DEFAULT,
        maxCells
      )
    eval(Map("identity" -> param))
  }
}
