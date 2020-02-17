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

package geotrellis.store

import geotrellis.raster.RasterSource
import geotrellis.vector.{Extent, ProjectedExtent}
import higherkindness.droste.data.Fix
import java.time.ZonedDateTime

package object query {
  type Query = Fix[QueryF]

  implicit class QueryOps(self: Query) {
    def or(r: Query): Query  = QueryF.or(self, r)
    def and(r: Query): Query = QueryF.and(self, r)
  }

  implicit class RasterSourceOps(self: RasterSource) {
    def projectedExtent: ProjectedExtent         = ProjectedExtent(self.extent, self.crs)
  }

  implicit class ProjectedExtentOps(self: ProjectedExtent) {
    def intersects(pe: ProjectedExtent): Boolean = self.extent.intersects(pe.reproject(self.crs))
    def covers(pe: ProjectedExtent): Boolean     = self.extent.covers(pe.reproject(self.crs))
    def contains(pe: ProjectedExtent): Boolean   = self.extent.contains(pe.reproject(self.crs))
  }

  def or(l: Query, r: Query): Query          = QueryF.or(l, r)
  def and(l: Query, r: Query): Query         = QueryF.and(l, r)
  def nothing: Query                         = QueryF.nothing
  def all: Query                             = QueryF.all
  def withName(name: String): Query          = QueryF.withName(name)
  def withNames(names: Set[String]): Query   = QueryF.withNames(names)
  def intersects(pe: ProjectedExtent): Query = QueryF.intersects(pe)
  def contains(pe: ProjectedExtent): Query   = QueryF.contains(pe)
  def covers(pe: ProjectedExtent): Query     = QueryF.covers(pe)
  def at(t: ZonedDateTime, fieldName: Symbol = 'time): Query                          = QueryF.at(t, fieldName)
  def between(t1: ZonedDateTime, t2: ZonedDateTime, fieldName: Symbol = 'time): Query = QueryF.between(t1, t2, fieldName)
}
