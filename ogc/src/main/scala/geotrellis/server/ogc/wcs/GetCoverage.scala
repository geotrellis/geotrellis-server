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

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.server._
import geotrellis.server.ogc._

import com.azavea.maml.error._
import com.azavea.maml.eval._
import cats.data.Validated._
import cats.effect._
import cats.syntax.flatMap._
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.concurrent.duration._

class GetCoverage(wcsModel: WcsModel) {
  val logger = org.log4s.getLogger

  /**
   * QGIS appears to sample WCS service by placing low and high resolution requests at coverage center.
   * These sampling requests happen for every actual WCS request, we can get really great cache hit rates.
   */
  lazy val requestCache: Cache[GetCoverageWcsParams, Array[Byte]] =
      Scaffeine()
        .recordStats()
        .expireAfterWrite(1.hour)
        .maximumSize(32)
        .build()

  def build(params: GetCoverageWcsParams)(implicit cs: ContextShift[IO]): IO[Array[Byte]] = {
    IO { requestCache.getIfPresent(params) } >>= {
      case Some(bytes) =>
        logger.trace(s"GetCoverage cache HIT: $params")
        IO.pure(bytes)

      case _ =>
        logger.trace(s"GetCoverage cache MISS: $params")
        val re = params.gridExtent

        val eval = wcsModel.getLayers(params).headOption.map {
          case so @ SimpleOgcLayer(_, _, _, _, _) => LayerExtent.identity(so)
          case MapAlgebraOgcLayer(_, _, _, simpleLayers, algebra, _) =>
            LayerExtent(IO.pure(algebra), IO.pure(simpleLayers), ConcurrentInterpreter.DEFAULT)
        }.getOrElse(throw new Exception("bad interpreter"))

        eval(re.extent, re.cellSize) map {
          case Valid(mbtile) =>
            val bytes = GeoTiff(Raster(mbtile, re.extent), params.crs).toByteArray
            requestCache.put(params, bytes)
            bytes
          case Invalid(errs) => throw MamlException(errs)
        }
    }
  }
}
