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
import geotrellis.store.azure
import geotrellis.raster.{EmptyName, RasterSource, SourceName, StringName}
import geotrellis.raster.geotiff.GeoTiffPath
import com.azavea.stac4s.{StacAsset, StacExtent, TemporalExtent}
import com.azavea.stac4s.api.client.{Query => SQuery, SearchFilters, StacClient}
import com.azavea.stac4s.extensions.periodic.PeriodicExtent
import com.azavea.stac4s.syntax._
import io.circe.syntax._
import cats.{Applicative, Foldable, Functor, FunctorFilter}
import cats.data.NonEmptyList
import cats.syntax.either._
import cats.syntax.foldable._
import cats.syntax.functorFilter._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.option._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.store.azure.util.{AzureRangeReaderProvider, AzureURI}

import java.net.URI
import java.time.ZoneOffset

package object stac {

  implicit class StacExtentionOps(val self: StacExtent) extends AnyVal {

    /** [[StacExtent]]s with no temporal component are valid. */
    def ogcTime: Option[OgcTime] =
      self.temporal.interval.headOption.map { case TemporalExtent(start, end) => List(start, end).flatten.map(_.atZone(ZoneOffset.UTC)) }.map {
        case fst :: Nil        => OgcTimeInterval(fst)
        case fst :: snd :: Nil => OgcTimeInterval(fst, snd, self.temporal.getExtensionFields[PeriodicExtent].map(_.period).toOption)
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

  implicit class StacAssetOps(val self: StacAsset) extends AnyVal {
    def hrefGDAL(withGDAL: Boolean): String    = if (withGDAL) s"gdal+${self.href}" else s"${GeoTiffPath.PREFIX}${self.href}"
    def withGDAL(withGDAL: Boolean): StacAsset = self.copy(href = hrefGDAL(withGDAL))
    def withAzureSupport(toWASBS: Boolean): StacAsset =
      if (toWASBS && AzureRangeReaderProvider.canProcess(self.href)) self.copy(href = AzureURI.fromString(self.href).toString) else self
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

    /**
     * A helper function that filters raster sources in case the STAC Layer is temporal and it is not taken into account in the query.
     *
     * By default STAC API returns all temporal items even though the time is not specified. If ignoreTime configuration is set to false and the query
     * is not temporal and not universal (meaning that it is bounded by temporal or spatial extent), we can select the first time position of the
     * temporal layer in this case.
     *
     * If the layer is not temporal, no extra filtering would be applied. All non temporal items would be included into the result. Otherwise, only
     * items that match the first time position would be returned.
     */
    def timeSlice(query: Query, timeDefault: OgcTimeDefault, ignoreTime: Boolean, datetimeField: Option[String]): G[T] =
      if (!ignoreTime & query.nonTemporal && query.nonUniversal) {
        self.foldMap(_.time(datetimeField)) match {
          case OgcTimePositions(list) =>
            self.filter(source => source.time(datetimeField).strictTimeMatch(timeDefault.selectTime(list)))
          case OgcTimeInterval(start, end, _) =>
            self.filter(source => source.time(datetimeField).strictTimeMatch(timeDefault.selectTime(NonEmptyList.of(start, end))))
          case OgcTimeEmpty => self
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

  implicit class RasterSourcesMQueryOps[M[_]: Functor, G[_]: Foldable: FunctorFilter: Functor, T <: RasterSource](val self: G[M[T]]) {

    /**
     * A helper function that filters raster sources in case the STAC Layer is temporal and it is not taken into account in the query.
     *
     * By default STAC API returns all temporal items even though the time is not specified. If ignoreTime configuration is set to false and the query
     * is not temporal and not universal (meaning that it is bounded by temporal or spatial extent), we can select the first time position of the
     * temporal layer in this case.
     *
     * If the layer is not temporal, no extra filtering would be applied. All non temporal items would be included into the result. Otherwise, only
     * items that match the first time position would be returned.
     */

    def timeSlice(query: Query, timeDefault: OgcTimeDefault, ignoreTime: Boolean, datetimeField: Option[String]): G[M[Option[T]]] =
      if (!ignoreTime & query.nonTemporal && query.nonUniversal) {
        self.map { sourceM =>
          sourceM.map { source =>
            source.time(datetimeField) match {
              case t @ OgcTimePositions(list) =>
                if (t.strictTimeMatch(timeDefault.selectTime(list))) source.some
                else None
              case t @ OgcTimeInterval(start, end, _) =>
                if (t.strictTimeMatch(timeDefault.selectTime(NonEmptyList.of(start, end)))) source.some
                else None
              case OgcTimeEmpty => source.some
            }
          }
        }
      } else self.map(_.map(_.some))
  }
}
