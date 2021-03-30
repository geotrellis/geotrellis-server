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

import geotrellis.stac._
import geotrellis.server.ogc.utils._
import geotrellis.store.query._

import geotrellis.raster.{EmptyName, RasterSource, SourceName, StringName}
import geotrellis.raster.geotiff.GeoTiffPath
import com.azavea.stac4s.{StacExtent, StacItemAsset}
import com.azavea.stac4s.api.client.{SearchFilters, StacClient, Query => SQuery}
import io.circe.syntax._
import cats.{Applicative, Foldable, FunctorFilter}
import cats.syntax.either._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.functorFilter._
import cats.syntax.applicative._
import eu.timepit.refined.types.string.NonEmptyString

import java.time.ZoneOffset

package object stac {
  implicit class StacExtentionOps(val self: StacExtent) extends AnyVal {

    /** [[StacExtent]]s with no temporal component are valid. */
    def ogcTime: Option[OgcTime] = self.temporal.interval.headOption.map(_.value.flatten.map(_.atZone(ZoneOffset.UTC))).map {
      case fst :: Nil        => OgcTimeInterval(fst)
      case fst :: snd :: Nil => OgcTimeInterval(fst, snd)
      case _                 => OgcTimeEmpty
    }
  }

  implicit class StacSummaryOps(val self: StacSummary) extends AnyVal {
    def sourceName: SourceName =
      self match {
        case CollectionSummary(asset) => asset.sourceName
        case EmptySummary             => EmptyName
      }
  }

  implicit class StacItemAssetOps(val self: StacItemAsset) extends AnyVal {
    def hrefGDAL(withGDAL: Boolean): String        = if (withGDAL) s"gdal+${self.href}" else s"${GeoTiffPath.PREFIX}${self.href}"
    def withGDAL(withGDAL: Boolean): StacItemAsset = self.copy(href = hrefGDAL(withGDAL))
  }

  implicit class QueryMapOps(val left: Map[String, List[SQuery]]) extends AnyVal {
    def deepMerge(right: Map[String, List[SQuery]]): Map[String, List[SQuery]] =
      left.asJson.deepMerge(right.asJson).as[Map[String, List[SQuery]]].valueOr(throw _)
  }

  implicit class SearchFiltersObjOps(val self: SearchFilters.type) extends AnyVal {
    def eval(stacSearchCriteria: StacSearchCriteria)(query: Query): Option[SearchFilters] = SearchFiltersQuery.eval(stacSearchCriteria)(query)
  }

  implicit class StacClientOps[F[_]: Applicative](val self: StacClient[F]) {
    def summary(query: Query, searchCriteria: StacSearchCriteria): F[StacSummary] =
      SearchFiltersQuery.evalSummary[F](searchCriteria)(query).apply(self)

    def summary(name: String, searchCriteria: StacSearchCriteria): F[StacSummary] =
      searchCriteria match {
        case ByLayer => EmptySummary.pure[F].widen
        case _       => self.collection(NonEmptyString.unsafeFrom(name)).map(CollectionSummary)
      }
  }

  implicit class RasterSourcesQueryOps[G[_]: Foldable: FunctorFilter, T <: RasterSource](val self: G[T]) {

    /** A helper function that filters raster sources in case the STAC Layer is temporal and it is not taken into account in the query.
      *
      * By default STAC API returns all temporal items even though the time is not specified.
      * If defaultTime configuration is set to true and the query is not temporal and not universal
      * (meaning that it is bounded by temporal or spatial extent),
      * we can select the first time position of the temporal layer in this case.
      *
      * If the layer is not temporal, no extra filtering would be applied.
      * All non temporal items would be included into the result.
      * Otherwise, only items that match the first time position would be returned.
      */
    def timeSlice(query: Query, defaultTime: Boolean, datetimeField: Option[String]): G[T] =
      if (defaultTime && query.nonTemporal && query.nonUniversal) {
        self.foldMap(_.time(datetimeField)) match {
          case OgcTimePositions(list)       =>
            val start = list.head
            self.filter(source => OgcTime.strictTimeMatch(source.time(datetimeField), start))
          case OgcTimeInterval(start, _, _) =>
            self.filter(source => OgcTime.strictTimeMatch(source.time(datetimeField), start))
          case OgcTimeEmpty                 => self
        }
      } else self

    /** Collects all RasterSources attributes and prefixing each result Map key with the RasterSource name. */
    def attributesByName: Map[String, String] =
      self.foldMap { rs =>
        rs.name match {
          case StringName(sn) => rs.attributes.map { case (k, v) => s"$sn-$k" -> v }
          case EmptyName      => rs.attributes
        }
      }
  }
}
