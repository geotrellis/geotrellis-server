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

import geotrellis.server.ogc.{OgcSource, SimpleSource}
import geotrellis.server.ogc.conf.{MapAlgebraSourceConf, StacSourceConf}
import geotrellis.store.query
import geotrellis.store.query._

import cats.effect.Sync
import cats.syntax.functor._
import cats.syntax.semigroup._
import cats.instances.list._
import higherkindness.droste.scheme
import org.http4s.client.Client
import io.chrisdavenport.log4cats.Logger

case class MapAlgebraStacOgcRepository[F[_]: Sync](
  mapAlgebraSourceConf: MapAlgebraSourceConf,
  stacSourceConfs: List[StacSourceConf],
  repository: RepositoryM[F, List, OgcSource]
) extends RepositoryM[F, List, OgcSource] {
  private val names = stacSourceConfs.map(_.name).distinct

  def store: F[List[OgcSource]] = find(query.all)

  /** Replace the OGC layer name with its STAC Layer name */
  private def queryWithName(query: Query)(name: String): Query =
    scheme.ana(QueryF.coalgebraWithName(name)).apply(query)

  def find(query: Query): F[List[OgcSource]] =
    repository
      .find(names.map(queryWithName(query)).fold(nothing)(_ or _))
      .map(_.collect { case ss: SimpleSource => ss })
      .map(mapAlgebraSourceConf.modelOpt(_).toList)
      .widen
}

case class MapAlgebraStacOgcRepositories[F[_]: Sync: Logger](
  mapAlgebraConfLayers: List[MapAlgebraSourceConf],
  stacLayers: List[StacSourceConf],
  client: Client[F]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] =
    find(query.withNames(mapAlgebraConfLayers.map(_.name).toSet))

  /**
    * At first, choose stacLayers that fit the query, because after that we'll erase their name.
    * GT Server layer conf names != the STAC Layer name
    * conf names can be different for the same STAC Layer name.
    * A name is unique per the STAC layer and an asset.
    */
  def find(query: Query): F[List[OgcSource]] =
    StacOgcRepositories
      .eval(query)(mapAlgebraConfLayers)
      .map { conf =>
        /** Extract layerNames from the MAML expression */
        val layerNames = conf.listParams(conf.algebra)

        /** Get all ogc layers that are required for the MAML expression evaluation */
        val stacLayersFiltered = stacLayers.filter(l => layerNames.contains(l.name))
        MapAlgebraStacOgcRepository[F](conf, stacLayersFiltered, StacOgcRepositories[F](stacLayersFiltered, client))
      }
      .fold(RepositoryM.empty[F, List, OgcSource])(_ |+| _)
      .find(query)
}
