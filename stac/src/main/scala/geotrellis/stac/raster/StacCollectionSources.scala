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

package geotrellis.stac.raster

import io.circe.syntax._
import geotrellis.raster._
import com.azavea.stac4s.{StacCollection, StacExtent}

case class StacCollectionSources(asset: StacCollection, sources: List[RasterSource]) extends StacSources[StacCollection] {
  val stacExtent: StacExtent = asset.extent
  val name: SourceName       = StringName(asset.id)

  lazy val attributes: Map[String, String] =
    asset.asJson.asObject
      .map(_.toMap)
      .getOrElse(Map.empty)
      .mapValues(_.toString)

  override def toString: String = s"StacCollectionSource($name, $asset, $sources)"
}
