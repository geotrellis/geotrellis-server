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

package geotrellis.server.ogc.stac

import geotrellis.store.query._
import geotrellis.store.query.QueryF._
import geotrellis.raster.{MosaicRasterSource, RasterSource}
import geotrellis.server.ogc.{OgcSource, OgcTime, OgcTimeEmpty, OgcTimeInterval, OgcTimePositions}
import geotrellis.server.ogc.conf.{OgcSourceConf, StacSourceConf}
import geotrellis.server.ogc.utils._
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import geotrellis.store.query
import geotrellis.stac.raster.StacAssetRasterSource
import com.azavea.stac4s.api.client.{Query => _, _}

import cats.data.NonEmptyList
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.option._
import cats.syntax.semigroup._
import cats.effect.Sync
import cats.instances.list._
import higherkindness.droste.{scheme, Algebra}
import io.chrisdavenport.log4cats.Logger

case class StacOgcRepository[F[_]: Sync: Logger](
  stacSourceConf: StacSourceConf,
  client: SttpStacClient[F]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] = find(query.all)

  def find(query: Query): F[List[OgcSource]] = {

    /** Replace the actual conf name with the STAC Layer name */
    val filters: Option[SearchFilters] = SearchFilters.eval(stacSourceConf.searchCriteria)(query.overrideName(stacSourceConf.searchName))
    filters.fold(List.empty[OgcSource].pure[F]) { filter =>
      client
        .search(filter.copy(limit = stacSourceConf.assetLimit))
        .map { items =>
          val rasterSources =
            items.flatMap { item =>
              item.assets
                .get(stacSourceConf.asset)
                .map { itemAsset =>
                  StacAssetRasterSource(
                    itemAsset.withGDAL(stacSourceConf.withGDAL),
                    item.properties
                  )
                }
            }

          val source: Option[RasterSource] = rasterSources match {
            case head :: Nil => head.some
            case head :: _   =>
              /**
                * By default STAC API returns all temporal items even though the time is not specified.
                * If defaultTime configuration is set to true and the query is not temporal and not universal
                * (meaning that it is bounded by temporal or spatial extent),
                * we can select the first time position of the temporal layer in this case.
                *
                * If the layer is not temporal, no extra filtering would be applied.
                * All non temporal items would be included into the result.
                * Otherwise, only items that match the first time position would be returned.
                */
              val sources            = if (stacSourceConf.defaultTime && query.nonTemporal && query.nonUniversal) {
                val datetimeField = stacSourceConf.datetimeField.some
                rasterSources.map(_.time(datetimeField)).reduce(_ |+| _) match {
                  case OgcTimePositions(list)       =>
                    val start = list.head
                    rasterSources.filter(source => OgcTime.strictTimeMatch(source.time(datetimeField), start))
                  case OgcTimeInterval(start, _, _) =>
                    rasterSources.filter(source => OgcTime.strictTimeMatch(source.time(datetimeField), start))
                  case OgcTimeEmpty                 => rasterSources
                }
              } else rasterSources
              val commonCrs          = if (sources.map(_.crs).distinct.size == 1) head.crs else stacSourceConf.commonCrs
              val reprojectedSources = sources.map(_.reproject(commonCrs))
              MosaicRasterSource.instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs).some
            case _           => None
          }

          source.map(stacSourceConf.toLayer).toList
        }
    }
  }
}

case class StacOgcRepositories[F[_]: Sync: Logger](
  stacLayers: List[StacSourceConf],
  client: SttpBackend[F, Any]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] = find(query.withNames(stacLayers.map(_.name).toSet))

  /**
    * At first, choose stacLayers that fit the query, because after that we'll erase their name.
    * GT Server layer conf names != the STAC Layer name
    * conf names can be different for the same STAC Layer name.
    * A name is unique per the STAC layer and an asset.
    */
  def find(query: Query): F[List[OgcSource]] =
    StacOgcRepositories
      .eval(query)(stacLayers)
      .map { conf => StacOgcRepository(conf, SttpStacClient(client, uri"${conf.source}")) }
      .fold(RepositoryM.empty[F, List, OgcSource])(_ |+| _)
      .find(query)
}

object StacOgcRepositories {
  def algebra[T <: OgcSourceConf]: Algebra[QueryF, List[T] => List[T]] =
    Algebra {
      case Nothing()        => _ => Nil
      case WithName(name)   => _.filter { _.name == name }
      case WithNames(names) => _.filter { c => names.contains(c.name) }
      case And(e1, e2)      =>
        list =>
          val left = e1(list); left intersect e2(left)
      case Or(e1, e2)       => list => e1(list) ++ e2(list)
      case _                => identity
    }

  def eval[T <: OgcSourceConf](query: Query)(list: List[T]): List[T] =
    scheme.cata(algebra[T]).apply(query)(list)
}
