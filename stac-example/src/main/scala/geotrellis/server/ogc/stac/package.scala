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

package geotrellis.server.ogc

import geotrellis.raster.geotiff.GeoTiffPath
import com.azavea.stac4s.StacItemAsset
import com.azavea.stac4s.api.client.{Query => SQuery}

import io.circe.syntax._
import cats.syntax.either._

package object stac {
  implicit class StacItemAssetOps(val self: StacItemAsset) extends AnyVal {
    def hrefGDAL(withGDAL: Boolean): String        = if (withGDAL) s"gdal+${self.href}" else s"${GeoTiffPath.PREFIX}${self.href}"
    def withGDAL(withGDAL: Boolean): StacItemAsset = self.copy(href = hrefGDAL(withGDAL))
  }

  implicit class QueryMapOps(val left: Map[String, List[SQuery]]) extends AnyVal {
    def deepMerge(right: Map[String, List[SQuery]]): Map[String, List[SQuery]] =
      left.asJson.deepMerge(right.asJson).as[Map[String, List[SQuery]]].valueOr(throw _)
  }
}
