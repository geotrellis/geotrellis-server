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

import geotrellis.stac._
import geotrellis.stac.raster.{StacAssetRasterSource, StacCollectionSource, StacItemAsset}
import geotrellis.stac.util.logging.syntax._
import geotrellis.store.query
import geotrellis.store.query._
import geotrellis.store.query.QueryF._
import geotrellis.raster.{EmptyName, MosaicRasterSource, RasterSource}
import geotrellis.server.ogc.OgcSource
import geotrellis.server.ogc.conf.{OgcSourceConf, StacSourceConf}
import geotrellis.raster.effects.MosaicRasterSourceIO
import sttp.client3.SttpBackend
import sttp.client3.UriContext
import com.azavea.stac4s.api.client.{Query => _, _}
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.effect.unsafe.IORuntime
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.option._
import cats.syntax.semigroup._
import cats.instances.list._
import higherkindness.droste.{scheme, Algebra}

/**
 * Sync is required to compile [[fs2.Stream]]
 */
case class StacOgcRepository[F[_]: Sync](
  stacSourceConf: StacSourceConf,
  client: StreamingStacClientFS2[F]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] = find(query.all)

  def find(query: Query): F[List[OgcSource]] = {

    /**
     * Replace the actual conf name with the STAC Layer name.
     */
    val filters: Option[SearchFilters] =
      SearchFilters
        .eval(stacSourceConf.searchCriteria)(query.overrideName(stacSourceConf.searchName))
        .map(_.copy(limit = stacSourceConf.pageLimit))

    filters.fold(List.empty[OgcSource].pure[F]) { filter =>
      /**
       * Query summary i.e. collection or layer summary and items and perform the matching items search.
       */
      val summary = client.summary(stacSourceConf.searchName, stacSourceConf.searchCriteria)
      val items = stacSourceConf.assetLimit.fold(client.search(filter))(limit => client.search(filter).take(limit.value))
      (summary, items.compile.toList)
        .mapN { case (summary, items) =>
          val rasterSources =
            items.flatMap { item =>
              item.assets
                .select(stacSourceConf.asset)
                .map(itemAsset =>
                  StacAssetRasterSource(StacItemAsset(itemAsset.withAzureSupport(stacSourceConf.toWASBS).withGDAL(stacSourceConf.withGDAL), item))
                )
            }

          summary match {
            case csummary: CollectionSummary =>
              val source: Option[StacCollectionSource] = rasterSources match {
                case head :: Nil => StacCollectionSource(csummary.asset, head).some
                case head :: _   =>
                  /**
                   * Extra temporal layers filtering (slicing). If the layer is not temporal, no extra filtering (slicing) would be applied.
                   */
                  val sources =
                    rasterSources.timeSlice(query, stacSourceConf.timeDefault, stacSourceConf.ignoreTime, stacSourceConf.datetimeField.some)
                  val commonCrs = if (sources.flatMap(_.asset.crs).distinct.size == 1) head.crs else stacSourceConf.commonCrs
                  val reprojectedSources = sources.map(_.reproject(commonCrs))
                  val attributes = reprojectedSources.attributesByName

                  // TODO: Fix the unsafe behavior, requires refactor of all repos and all RasterSources usages
                  val mosaicRasterSource =
                    if (stacSourceConf.parallelMosaic)
                      MosaicRasterSourceIO.instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs, csummary.sourceName, attributes)(
                        IORuntime.global
                      )
                    else
                      MosaicRasterSource.instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs, csummary.sourceName, attributes)

                  /**
                   * In case some of the RasterSources are not from the STAC collection, we'd need to expand the [[StacCollectionSource]] extent.
                   */
                  StacCollectionSource(csummary.asset.expandExtentToInclude(mosaicRasterSource.extent), mosaicRasterSource).some
                case _ => None
              }

              source.map(stacSourceConf.toLayer).toList
            case _ =>
              val source: Option[RasterSource] = rasterSources match {
                case head :: Nil => head.some
                case head :: _   =>
                  /**
                   * Extra temporal layers filtering (slicing). If the layer is not temporal, no extra filtering (slicing) would be applied.
                   */
                  val sources =
                    rasterSources.timeSlice(query, stacSourceConf.timeDefault, stacSourceConf.ignoreTime, stacSourceConf.datetimeField.some)
                  val commonCrs = if (sources.flatMap(_.asset.crs).distinct.size == 1) head.crs else stacSourceConf.commonCrs
                  val reprojectedSources = sources.map(_.reproject(commonCrs))
                  val attributes = reprojectedSources.attributesByName

                  // TODO: Fix the unsafe behavior, requires refactor of all repos and all RasterSources usages
                  if (stacSourceConf.parallelMosaic)
                    MosaicRasterSourceIO
                      .instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs, EmptyName, attributes)(IORuntime.global)
                      .some
                  else
                    MosaicRasterSource.instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs, EmptyName, attributes).some
                case _ => None
              }

              source.map(stacSourceConf.toLayer).toList
          }
        }
    }
  }
}

case class StacOgcRepositories[F[_]: Sync](
  stacLayers: List[StacSourceConf],
  client: SttpBackend[F, Any]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] = find(query.withNames(stacLayers.map(_.name).toSet))

  /**
   * At first, choose stacLayers that fit the query, because after that we'll erase their name. GT Server layer conf names != the STAC Layer name conf
   * names can be different for the same STAC Layer name. A name is unique per the STAC layer and an asset.
   */
  def find(query: Query): F[List[OgcSource]] =
    StacOgcRepositories
      .eval(query)(stacLayers)
      .map(conf => StacOgcRepository(conf, SttpStacClient(client, uri"${conf.source}").withLogging))
      .fold(RepositoryM.empty[F, List, OgcSource])(_ |+| _)
      .find(query)
}

object StacOgcRepositories {
  def algebra[T <: OgcSourceConf]: Algebra[QueryF, List[T] => List[T]] =
    Algebra {
      case Nothing()        => _ => Nil
      case WithName(name)   => _.filter(_.name == name)
      case WithNames(names) => _.filter(c => names.contains(c.name))
      case And(e1, e2) =>
        list =>
          val left = e1(list); left.intersect(e2(left))
      case Or(e1, e2) => list => e1(list) ++ e2(list)
      case _          => identity
    }

  def eval[T <: OgcSourceConf](query: Query)(list: List[T]): List[T] =
    scheme.cata(algebra[T]).apply(query)(list)
}
