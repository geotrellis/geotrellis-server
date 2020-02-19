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
import geotrellis.vector.ProjectedExtent

import io.circe.{Decoder, Encoder}
import higherkindness.droste.data.Fix

import java.time.ZonedDateTime

package object query {
  type Query = Fix[QueryF]

  implicit val queryEncoder: Encoder[Query] = Encoder.encodeJson.contramap(QueryF.asJson)
  implicit val querDecoder: Decoder[Query] = Decoder.decodeJson.map(QueryF.fromJson)

  implicit class QueryOps(self: Query) {
    def or(right: Query): Query  = QueryF.or(self, right)
    def and(right: Query): Query = QueryF.and(self, right)
  }

  def or(left: Query, right: Query): Query                = QueryF.or(left, right)
  def and(left: Query, right: Query): Query               = QueryF.and(left, right)
  def nothing: Query                                      = QueryF.nothing
  def all: Query                                          = QueryF.all
  def withName(name: String): Query                       = QueryF.withName(name)
  def withNames(names: Set[String]): Query                = QueryF.withNames(names)
  def intersects(projectedExtent: ProjectedExtent): Query = QueryF.intersects(projectedExtent)
  def contains(projectedExtent: ProjectedExtent): Query   = QueryF.contains(projectedExtent)
  def covers(projectedExtent: ProjectedExtent): Query     = QueryF.covers(projectedExtent)
  def at(time: ZonedDateTime, fieldName: String = "time"): Query                         = QueryF.at(time, fieldName)
  def between(from: ZonedDateTime, to: ZonedDateTime, fieldName: String = "time"): Query = QueryF.between(from, to, fieldName)

  implicit class RasterSourceOps(self: RasterSource) {
    def projectedExtent: ProjectedExtent = ProjectedExtent(self.extent, self.crs)
  }

  implicit class ProjectedExtentOps(self: ProjectedExtent) {
    def intersects(projectedExtent: ProjectedExtent): Boolean = self.extent.intersects(projectedExtent.reproject(self.crs))
    def covers(projectedExtent: ProjectedExtent): Boolean     = self.extent.covers(projectedExtent.reproject(self.crs))
    def contains(projectedExtent: ProjectedExtent): Boolean   = self.extent.contains(projectedExtent.reproject(self.crs))
  }
}
