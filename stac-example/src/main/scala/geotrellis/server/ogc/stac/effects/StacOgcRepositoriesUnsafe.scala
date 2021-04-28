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

package geotrellis.server.ogc.stac.effects

import cats.data.NonEmptyList
import cats.effect.ContextShift
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.option._
import cats.syntax.semigroup._
import cats.{MonadThrow, Parallel}
import com.azavea.stac4s.api.client.{Query => _, _}
import geotrellis.raster.effects.syntax._
import geotrellis.raster.effects.{MosaicRasterSourceIO, UnsafeLift, MosaicRasterSource => MosaicRasterSourceE}
import geotrellis.raster.{EmptyName, MosaicRasterSource, RasterSource}
import geotrellis.server.ogc.stac._
import geotrellis.server.ogc.OgcSource
import geotrellis.server.ogc.conf.StacSourceConf
import geotrellis.server.ogc.stac.{CollectionSummary, StacOgcRepositories}
import geotrellis.stac._
import geotrellis.stac.raster.{StacAssetRasterSource, StacCollectionSource, StacItemAsset}
import geotrellis.store.query
import geotrellis.store.query._
import sttp.client3.{SttpBackend, UriContext}

case class StacOgcRepositoryUnsafe[F[_]: Parallel: UnsafeLift: MonadThrow: ContextShift](
  stacSourceConf: StacSourceConf,
  client: SttpStacClient[F]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] = find(query.all)

  def find(query: Query): F[List[OgcSource]] = {

    /** Replace the actual conf name with the STAC Layer name. */
    val filters: Option[SearchFilters] =
      SearchFilters
        .eval(stacSourceConf.searchCriteria)(query.overrideName(stacSourceConf.searchName))
        .map(_.copy(limit = stacSourceConf.assetLimit))

    filters.fold(List.empty[OgcSource].pure[F]) { filter =>
      /** Query summary i.e. collection or layer summary and items and perform the matching items search. */
      (client.summary(stacSourceConf.searchName, stacSourceConf.searchCriteria), client.search(filter))
        .mapN { case (summary, items) =>
          val rasterSources =
            items.flatMap { item =>
              item.assets
                .get(stacSourceConf.asset)
                .map { itemAsset => StacAssetRasterSource(StacItemAsset(itemAsset.withGDAL(stacSourceConf.withGDAL), item)) }
            }

          summary match {
            case csummary: CollectionSummary =>
              val source: Option[StacCollectionSource] = rasterSources match {
                case head :: _ =>
                  /** Extra temporal layers filtering (slicing). If the layer is not temporal, no extra filtering (slicing) would be applied. */
                  val sources            =
                    rasterSources.timeSlice(query, stacSourceConf.timeDefault, stacSourceConf.ignoreTime, stacSourceConf.datetimeField.some)
                  val commonCrs          = if (sources.flatMap(_.asset.crs).distinct.size == 1) head.crs else stacSourceConf.commonCrs
                  val reprojectedSources = sources.map(_.reproject(commonCrs))
                  val attributes         = reprojectedSources.attributesByName

                  // TODO: Fix the unsafe behavior, requires refactor of all repos and all RasterSources usages
                  val mosaicRasterSource =
                    if (stacSourceConf.parallelMosaic)
                      MosaicRasterSourceIO(
                        MosaicRasterSourceE.instanceIO[F](
                          NonEmptyList
                            .fromListUnsafe(reprojectedSources.map(_.pure[F].shift))
                            .lifts[F],
                          commonCrs.pure[F],
                          csummary.sourceName,
                          attributes.pure[F]
                        )
                      )
                    else
                      MosaicRasterSource.instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs, csummary.sourceName, attributes)

                  /** In case some of the RasterSources are not from the STAC collection, we'd need to expand the [[StacCollectionSource]] extent. */
                  StacCollectionSource(csummary.asset.expandExtentToInclude(mosaicRasterSource.extent), mosaicRasterSource).some
                case _         => None
              }

              source.map(stacSourceConf.toLayer).toList
            case _                           =>
              val source: Option[RasterSource] = rasterSources match {
                case head :: Nil => head.some
                case head :: _   =>
                  /** Extra temporal layers filtering (slicing). If the layer is not temporal, no extra filtering (slicing) would be applied. */
                  val sources            =
                    rasterSources.timeSlice(query, stacSourceConf.timeDefault, stacSourceConf.ignoreTime, stacSourceConf.datetimeField.some)
                  val commonCrs          = if (sources.flatMap(_.asset.crs).distinct.size == 1) head.crs else stacSourceConf.commonCrs
                  val reprojectedSources = sources.map(_.reproject(commonCrs))
                  val attributes         = reprojectedSources.attributesByName

                  // TODO: Fix the unsafe behavior, requires refactor of all repos and all RasterSources usages
                  if (stacSourceConf.parallelMosaic)
                    MosaicRasterSourceIO(
                      MosaicRasterSourceE.instanceIO[F](
                        NonEmptyList
                          .fromListUnsafe(reprojectedSources.map(_.pure[F].shift))
                          .lifts[F],
                        commonCrs.pure[F],
                        EmptyName,
                        attributes.pure[F]
                      )
                    ).some
                  else
                    MosaicRasterSource.instance(NonEmptyList.fromListUnsafe(reprojectedSources), commonCrs, EmptyName, attributes).some
                case _           => None
              }

              source.map(stacSourceConf.toLayer).toList
          }
        }
    }
  }
}

case class StacOgcRepositoriesUnsafe[F[_]: Parallel: UnsafeLift: MonadThrow: ContextShift](
  stacLayers: List[StacSourceConf],
  client: SttpBackend[F, Any]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] = find(query.withNames(stacLayers.map(_.name).toSet))

  /** At first, choose stacLayers that fit the query, because after that we'll erase their name.
    * GT Server layer conf names != the STAC Layer name
    * conf names can be different for the same STAC Layer name.
    * A name is unique per the STAC layer and an asset.
    */
  def find(query: Query): F[List[OgcSource]] =
    StacOgcRepositories
      .eval(query)(stacLayers)
      .map { conf => StacOgcRepositoryUnsafe(conf, SttpStacClient(client, uri"${conf.source}")) }
      .fold(RepositoryM.empty[F, List, OgcSource])(_ |+| _)
      .find(query)
}
