/*
 * Copyright 2021 Azavea
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

package geotrellis.server.ogc.wms

import cats.data.{NonEmptyList, Validated}
import geotrellis.server.LayerExtent
import geotrellis.server.ogc.{FeatureCollection, MapAlgebraOgcLayer, SimpleOgcLayer}
import geotrellis.server.ogc.wms.WmsParams.GetFeatureInfoExtendedParams
import geotrellis.server.utils.throwableExtensions
import cats.data.Validated.{Invalid, Valid}
import cats.effect.{Async, Concurrent}
import cats.{ApplicativeThrow, Parallel}
import cats.syntax.nested._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.parallel._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.syntax.applicativeError._
import com.azavea.maml.error.MamlError
import org.typelevel.log4cats.Logger
import io.circe._
import io.circe.syntax._
import com.azavea.maml.eval.ConcurrentInterpreter
import geotrellis.raster.{io => _, _}
import geotrellis.vector.{io => _, _}
import com.github.blemale.scaffeine.Cache
import opengis.wms._
import opengis._
import scalaxb._

case class GetFeatureInfoExtended[F[_]: Logger: Parallel: Async: ApplicativeThrow](
  model: WmsModel[F],
  // cache rasters by the asset name and point
  rasterCache: Cache[(String, Extent), MultibandTile]
) {
  def build(params: GetFeatureInfoExtendedParams): F[Either[GetFeatureInfoException, FeatureCollection[Geometry, Json]]] =
    // TODO: replace map / flatMap + sequence calls with traverse
    // NOTE: that requires a little bit of work with implicits, since Parallel and Traverse both in scope may cause implicits ambiguity for Functor and Monad
    model
      .getLayer(params)
      .flatMap { layers =>
        layers.flatMap { layer =>
          // TODO: move it into GeoTrellis
          val mp = params.multiPoint
          val points = (0 until mp.getNumPoints).map(idx => mp.getGeometryN(idx).asInstanceOf[Point])

          val (evalExtent, cs) = layer match {
            case sl: SimpleOgcLayer =>
              val cs = params.cellSize.getOrElse(sl.source.gridExtent.toRasterExtent.reproject(sl.source.crs, sl.crs).cellSize)
              (LayerExtent.withCellType(sl, sl.source.cellType), cs)
            case ml: MapAlgebraOgcLayer =>
              val cs = params.cellSize.getOrElse(
                ml.parameters.values.toList
                  .map { o =>
                    val source = o.source
                    source.gridExtent.toRasterExtent().reproject(source.crs, o.crs).cellSize
                  }
                  .minBy(_.resolution)
              )
              (LayerExtent(ml.algebra.pure[F], ml.parameters.pure[F], ConcurrentInterpreter.DEFAULT[F], ml.targetCellType), cs)
          }

          // generate tiny extents for the evalExtent, buffer to avoid border collisions
          // val extents = points.map(p => (p, Extent(p.getX - cs.width, p.getY - cs.height, p.getX + cs.width , p.getY + cs.height)))
          // read a bit buffered chunks so we're not hitting borders
          val extents = points.map(p => (p, Extent(p.getX - cs.width * 10, p.getY - cs.height * 10, p.getX + cs.width * 10, p.getY + cs.height * 10)))

          extents.map { case (p, e) =>
            val cacheKey = (layer.name, e)
            val evaluated: F[Validated[NonEmptyList[MamlError], MultibandTile]] = rasterCache.getIfPresent(cacheKey) match {
              case Some(mbtile) => Valid(mbtile).toValidatedNel.pure[F].widen
              case _ =>
                evalExtent(e, Some(cs)).map {
                  case Valid(mbtile) => Valid(mbtile)
                  case Invalid(errs) => Invalid(errs)
                }
            }

            evaluated.attempt.flatMap {
              case Right(Valid(mbtile)) => // success
                val raster = Raster(mbtile, e)
                rasterCache.put(cacheKey, mbtile)
                featureFromRaster(raster, p).pure[F].widen
              case Right(Invalid(errs)) => // maml-specific errors
                Logger[F].debug(errs.toList.toString).as(Left(LayerNotDefinedException(errs.toList.toString, params.version))).widen
              case Left(err) => // exceptions
                Logger[F].error(err.stackTraceString).as(Left(LayerNotDefinedException(err.stackTraceString, params.version))).widen
            }: F[Either[GetFeatureInfoException, Feature[Geometry, Json]]]
          }
        }.parSequence
      }
      .map(_.sequence)
      .nested
      .map(FeatureCollection(_))
      .value

  def featureFromRaster(
    raster: Raster[MultibandTile],
    p: Point
  ): Either[GetFeatureInfoException, Feature[Geometry, Json]] = {
    val (c, r) = raster.rasterExtent.mapToGrid(p)
    Right(Feature(p, raster.tile.bands.zipWithIndex.map { case (b, i) => s"band-$i-value" -> b.getDouble(c, r) }.toMap.asJson))
  }
}
