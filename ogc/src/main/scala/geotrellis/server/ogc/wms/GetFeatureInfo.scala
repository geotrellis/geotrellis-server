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

import geotrellis.server.LayerExtent
import geotrellis.server.ogc.{MapAlgebraOgcLayer, SimpleOgcLayer}
import geotrellis.server.ogc.wms.WmsParams.{GetFeatureInfoParams, GetMapParams}
import geotrellis.server.utils.throwableExtensions

import cats.data.Validated.{Invalid, Valid}
import cats.effect.Concurrent
import cats.{ApplicativeThrow, Parallel}
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.syntax.traverse._
import cats.syntax.applicativeError._
import io.chrisdavenport.log4cats.Logger
import io.circe.syntax._
import io.circe.Json
import com.azavea.maml.eval.ConcurrentInterpreter
import geotrellis.raster._
import geotrellis.vector.Feature
import com.github.blemale.scaffeine.Cache
import opengis.wms._
import opengis._
import org.locationtech.jts.geom.Polygon
import scalaxb._

case class GetFeatureInfo[F[_]: Logger: Parallel: Concurrent: ApplicativeThrow](
  model: WmsModel[F],
  rasterCache: Cache[GetMapParams, Raster[MultibandTile]]
) {
  def build(params: GetFeatureInfoParams): F[Either[GetFeatureInfoException, Feature[Polygon, Map[String, Json]]]] = {
    val re  = RasterExtent(params.boundingBox, params.width, params.height)
    val res = model
      .getLayer(params.toGetMapParamsQuery)
      .flatMap { layers =>
        layers
          .map { layer =>
            val evalExtent = layer match {
              case sl: SimpleOgcLayer     => LayerExtent.concurrent(sl)
              case ml: MapAlgebraOgcLayer =>
                LayerExtent(ml.algebra.pure[F], ml.parameters.pure[F], ConcurrentInterpreter.DEFAULT[F])
            }

            evalExtent(re.extent, re.cellSize.some).map {
              case Valid(mbtile) => Valid(mbtile)
              case Invalid(errs) => Invalid(errs)
            }.attempt flatMap {
              case Right(Valid(mbtile)) => // success
                val raster = Raster(mbtile, re.extent)
                rasterCache.put(params.toGetMapParams, raster)
                featureFromRaster(raster, params).pure[F].widen
              case Right(Invalid(errs)) => // maml-specific errors
                Logger[F].debug(errs.toList.toString).as(Left(LayerNotDefinedException(errs.toList.toString, params.version))).widen
              case Left(err)            => // exceptions
                Logger[F].error(err.stackTraceString).as(Left(LayerNotDefinedException(err.stackTraceString, params.version))).widen
            }: F[Either[GetFeatureInfoException, Feature[Polygon, Map[String, Json]]]]
          }
          .headOption
          .sequence
      }

    rasterCache.getIfPresent(params.toGetMapParams) match {
      case Some(raster) => featureFromRaster(raster, params).pure[F]
      case _            => res.map { _.getOrElse(Left(LayerNotDefinedException(s"Layers ${params.queryLayers} not found", params.version))) }
    }
  }

  def featureFromRaster(
    raster: Raster[MultibandTile],
    params: GetFeatureInfoParams
  ): Either[GetFeatureInfoException, Feature[Polygon, Map[String, Json]]] = {
    val Dimensions(cols, rows) = raster.dimensions

    if ((params.i < 0 && params.i >= cols) || (params.j < 0 && params.j >= rows))
      Left(InvalidPointException(s"${params.i}, ${params.j} not in dimensions of image: $cols, $rows", params.version))
    else
      Right(
        Feature(
          raster.extent.toPolygon(),
          Map(
            "per_band_pixel_values" ->
            raster.tile.bands.zipWithIndex.map { case (b, i) => i.toString -> b.get(params.i, params.j).toString }.toMap.asJson
          )
        )
      )
  }
}
