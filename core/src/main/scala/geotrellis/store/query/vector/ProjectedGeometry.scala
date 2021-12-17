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

package geotrellis.store.query.vector

import geotrellis.proj4.CRS
import geotrellis.vector.{io => _, _}
import io.circe.generic.JsonCodec

@JsonCodec
case class ProjectedGeometry(geometry: Geometry, crs: CRS) {
  def reproject(dest: CRS): ProjectedGeometry = ProjectedGeometry(geometry.reproject(crs, dest), dest)

  def intersects(that: ProjectedGeometry): Boolean = geometry.intersects(that.reproject(crs).geometry)
  def covers(that: ProjectedGeometry): Boolean     = geometry.covers(that.reproject(crs).geometry)
  def contains(that: ProjectedGeometry): Boolean   = geometry.contains(that.reproject(crs).geometry)

  def toProjectedExtent: ProjectedExtent = ProjectedExtent(geometry.extent, crs)
}

object ProjectedGeometry {
  def apply(projectedExtent: ProjectedExtent): ProjectedGeometry =
    ProjectedGeometry(projectedExtent.extent.toPolygon(), projectedExtent.crs)

  def apply(extent: Extent, crs: CRS): ProjectedGeometry = apply(ProjectedExtent(extent, crs))
}
