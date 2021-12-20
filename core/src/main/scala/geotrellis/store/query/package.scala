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

import cats.Id
import geotrellis.raster.RasterSource
import geotrellis.store.query.vector.ProjectedGeometry
import geotrellis.vector.ProjectedExtent
import higherkindness.droste.data.Fix
import io.circe.{Decoder, Encoder}

import java.time.ZonedDateTime

package object query {
  type Query         = Fix[QueryF]
  type Repository[T] = RepositoryM[Id, List, T]

  implicit val queryEncoder: Encoder[Query] = Encoder.encodeJson.contramap(QueryF.asJson)
  implicit val queryDecoder: Decoder[Query] = Decoder.decodeJson.map(QueryF.fromJson)

  implicit class QueryOps(val self: Query) extends AnyVal {
    def or(right: Query): Query           = QueryF.or(self, right)
    def and(right: Query): Query          = QueryF.and(self, right)
    def isTemporal: Boolean               = QueryF.isTemporal(self)
    def nonTemporal: Boolean              = !isTemporal
    def isUniversal: Boolean              = QueryF.isUniversal(self)
    def nonUniversal: Boolean             = !isUniversal
    def overrideName(name: String): Query = QueryF.overrideName(self, name)
  }

  def or(left: Query, right: Query): Query                       = QueryF.or(left, right)
  def and(left: Query, right: Query): Query                      = QueryF.and(left, right)
  def nothing: Query                                             = QueryF.nothing
  def all: Query                                                 = QueryF.all
  def withName(name: String): Query                              = QueryF.withName(name)
  def withNames(names: Set[String]): Query                       = QueryF.withNames(names)
  def intersects(projectedGeometry: ProjectedGeometry): Query    = QueryF.intersects(projectedGeometry)
  def contains(projectedGeometry: ProjectedGeometry): Query      = QueryF.contains(projectedGeometry)
  def covers(projectedGeometry: ProjectedGeometry): Query        = QueryF.covers(projectedGeometry)
  def at(time: ZonedDateTime, fieldName: String = "time"): Query = QueryF.at(time, fieldName)

  def between(from: ZonedDateTime, to: ZonedDateTime, fieldName: String = "time"): Query =
    QueryF.between(from, to, fieldName)

  implicit class RasterSourceOps(self: RasterSource) {
    def projectedExtent: ProjectedExtent     = ProjectedExtent(self.extent, self.crs)
    def projectedGeometry: ProjectedGeometry = ProjectedGeometry(projectedExtent)
  }

  implicit class ProjectedExtentOps(val self: ProjectedExtent) extends AnyVal {
    def intersects(projectedExtent: ProjectedExtent): Boolean = self.extent.intersects(projectedExtent.reproject(self.crs))
    def covers(projectedExtent: ProjectedExtent): Boolean     = self.extent.covers(projectedExtent.reproject(self.crs))
    def contains(projectedExtent: ProjectedExtent): Boolean   = self.extent.contains(projectedExtent.reproject(self.crs))
  }

  implicit class RepositoryMOps[M[_], G[_], T](val self: RepositoryM[M, G, T]) extends AnyVal {
    def toF[F[_]](implicit ev: RepositoryM[M, G, T] => RepositoryM[F, G, T]): RepositoryM[F, G, T] = ev(self)
  }
}
