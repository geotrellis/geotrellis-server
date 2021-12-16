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

import com.azavea.stac4s.StacExtent
import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster.{RasterSource, SourceName}
import geotrellis.stac._
import geotrellis.vector.{Extent, ProjectedExtent}

trait StacSources[T] {
  val crs: CRS                         = LatLng
  def projectedExtent: ProjectedExtent = ProjectedExtent(extent, crs)
  def extent: Extent                   = stacExtent.spatial.toExtent

  def asset: T
  def name: SourceName
  def stacExtent: StacExtent
  // native projection can be not LatLng
  def sources: List[RasterSource]

  def attributes: Map[String, String]
}
