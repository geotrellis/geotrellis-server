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
import geotrellis.server.ogc.OgcSource
import geotrellis.server.ogc.conf.{OgcSourceConf, StacSourceConf}
import geotrellis.stac.api.{Http4sStacClient, SearchFilters, StacClient}
import geotrellis.store.query

import cats.Applicative
import cats.data.NonEmptyList
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.semigroup._
import cats.effect.Sync
import cats.instances.list._
import geotrellis.stac.raster.StacAssetRasterSource
import higherkindness.droste.{scheme, Algebra}
import org.http4s.Uri
import org.http4s.client.Client

case class StacOgcRepository[F[_]: Sync](
  stacSourceConf: StacSourceConf,
  client: StacClient[F]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] = find(query.all)

  /** Replace the OGC layer name with its STAC Layer name */
  private def queryWithName(query: Query): Query =
    scheme.ana(QueryF.coalgebraWithName(stacSourceConf.layer)).apply(query)

  def find(query: Query): F[List[OgcSource]] = {

    /** Replace the actual conf name with the STAC Layer name */
    val filters = SearchFilters.eval(queryWithName(query))

    filters match {
      case Some(filter) =>
        client
          .search(filter.copy(limit = stacSourceConf.assetLimit))
          .map { items =>
            val rasterSources =
              items.flatMap { item =>
                item.assets
                  .get(stacSourceConf.asset)
                  .map(StacAssetRasterSource(_, item.properties))
              }

            val source: Option[RasterSource] = rasterSources match {
              case head :: Nil => head.some
              case head :: _   =>
                val commonCrs          = if (rasterSources.map(_.crs).distinct.size == 1) head.crs else stacSourceConf.commonCrs
                val reprojectedSources = rasterSources.map(_.reproject(commonCrs))
                MosaicRasterSource.instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs).some
              case _           => None
            }

            source.map(stacSourceConf.toLayer).toList
          }
      case _            => Applicative[F].pure(Nil)
    }
  }
}

case class StacOgcRepositories[F[_]: Sync](
  stacLayers: List[StacSourceConf],
  client: Client[F]
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
      .map { conf => StacOgcRepository(conf, Http4sStacClient[F](client, Uri.unsafeFromString(conf.source))) }
      .reduce[RepositoryM[F, List, OgcSource]](_ |+| _)
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
