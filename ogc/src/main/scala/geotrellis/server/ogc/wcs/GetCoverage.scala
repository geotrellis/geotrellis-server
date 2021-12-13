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

package geotrellis.server.ogc.wcs

import geotrellis.store.query._
import geotrellis.raster.{io => _, _}
import geotrellis.raster.io.geotiff._
import geotrellis.server._
import geotrellis.server.ogc._

import com.azavea.maml.error._
import com.azavea.maml.eval._
import cats.Parallel
import cats.data.Validated._
import cats.effect._
import cats.syntax.apply._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.instances.option._
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration._

class GetCoverage[F[_]: Concurrent: Parallel: Logger](wcsModel: WcsModel[F]) {
  def renderLayers(params: GetCoverageWcsParams): F[Option[Array[Byte]]] = {
    val e  = params.extent
    val cs = params.cellSize
    wcsModel
      .getLayers(params)
      .flatMap {
        _.headOption
          .map {
            case so: SimpleOgcLayer => LayerExtent.withCellType(so, so.source.cellType)
            case mal: MapAlgebraOgcLayer =>
              LayerExtent(mal.algebra.pure[F], mal.parameters.pure[F], ConcurrentInterpreter.DEFAULT[F], mal.targetCellType)
          }
          .traverse { eval =>
            eval(e, cs) map {
              case Valid(mbtile) =>
                val bytes = Raster(mbtile, e).render(params.crs, None, params.format, Nil)
                requestCache.put(params, bytes)
                bytes
              case Invalid(errs) => throw MamlException(errs)
            }
          }
      }

  }

  /**
   * QGIS appears to sample WCS service by placing low and high resolution requests at coverage center. These sampling requests happen for every
   * actual WCS request, we can get really great cache hit rates.
   */
  lazy val requestCache: Cache[GetCoverageWcsParams, Array[Byte]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(1.hour)
      .maximumSize(32)
      .build()

  def build(params: GetCoverageWcsParams): F[Array[Byte]] =
    Sync[F].delay(requestCache.getIfPresent(params)) >>= {
      case Some(bytes) =>
        Logger[F].trace(s"GetCoverage cache HIT: $params") *> bytes.pure[F]

      case _ =>
        Logger[F].trace(s"GetCoverage cache MISS: $params") >>= { _ =>
          renderLayers(params).flatMap {
            case Some(bytes) => bytes.pure[F]
            case None =>
              wcsModel.sources
                .find(withName(params.identifier))
                .flatMap {
                  _.headOption match {
                    case Some(_) =>
                      // Handle the case where the STAC item was requested for some area between the tiles.
                      // STAC search will return an empty list, however QGIS may expect a test pixel to
                      // return the actual tile
                      // TODO: handle it in a proper way, how to get information about the bands amount?
                      val tile = ArrayTile.empty(IntUserDefinedNoDataCellType(0), 1, 1)
                      Raster(MultibandTile(tile, tile, tile), params.extent).render(params.crs, None, params.format, Nil).pure[F]
                    case _ => Logger[F].error(s"No tile found for the $params request.") *> Array[Byte]().pure[F]
                  }
                }
          }
        }

    }
}
