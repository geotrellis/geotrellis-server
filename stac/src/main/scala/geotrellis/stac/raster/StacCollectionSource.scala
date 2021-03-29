/*
 * Copyright 2019 Azavea
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

import geotrellis.stac._
import geotrellis.raster._
import geotrellis.vector._
import com.azavea.stac4s.StacCollection

case class StacCollectionSource(asset: StacCollection, source: RasterSource) extends StacSource[StacCollection] {
  val extent: Extent   = asset.extent.spatial.toExtent
  val name: SourceName = StringName(asset.id)

  lazy val attributes: Map[String, String] =
    asset.asJson.asObject
      .map(_.toMap)
      .getOrElse(Map.empty)
      .mapValues(_.toString)

  override def toString: String = s"StacCollectionSource($name, $asset, $source)"
}
