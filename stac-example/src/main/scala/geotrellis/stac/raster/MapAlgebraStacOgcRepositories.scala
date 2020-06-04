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

package geotrellis.stac.raster

import geotrellis.server.ogc.{OgcSource, SimpleSource}
import geotrellis.server.ogc.conf.{MapAlgebraSourceConf, StacSourceConf}
import geotrellis.store.query
import geotrellis.store.query._
import geotrellis.store.query.QueryF._

import cats.effect.IO
import higherkindness.droste.{Algebra, scheme}
import org.http4s.client.Client

case class MapAlgebraStacOgcRepository(
  mapAlgebraSourceConf: MapAlgebraSourceConf,
  stacSourceConfs: List[StacSourceConf],
  repository: Repository[List, OgcSource]
) extends Repository[List, OgcSource] {
  val names = stacSourceConfs.map(_.name).toSet

  /** Replace the name of the MAML MapalgebraSource with the name of each layer */
  private def queryWithName(query: Query)(name: String): Query =
    scheme.ana(QueryF.coalgebraWithName(name)).apply(query)

  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] =
    mapAlgebraSourceConf.modelOpt(
      names
        .toList
        .map(queryWithName(query))
        .flatMap(repository.find)
        .collect { case ss @ SimpleSource(_, _, _, _, _, _, _) => ss }
    ).toList
}

case class MapAlgebraStacOgcRepositories(mapAlgebraConfLayers: List[MapAlgebraSourceConf], stacLayers: List[StacSourceConf], client: Client[IO]) extends Repository[List, OgcSource] {
  def store: List[OgcSource] = find(query.all)

  /**
   * At first, choose stacLayers that fit the query, because after that we'll erase their name.
   * GT Server layer conf names != the STAC Layer name
   * conf names can be different for the same STAC Layer name.
   * A name is unique per the STAC layer and an asset.
   */
  def find(query: Query): List[OgcSource] =
    MapAlgebraStacOgcRepositories
      .eval(query)(mapAlgebraConfLayers)
      .map { conf =>
        /** Extract layerNames from the MAML expression */
        val layerNames = conf.listParams(conf.algebra)
        /** Get all ogc layers that are required for the MAML expression evaluation */
        val stacLayersFiltered = stacLayers.filter(l => layerNames.contains(l.name))
        MapAlgebraStacOgcRepository(conf, stacLayersFiltered, StacOgcRepositories(stacLayersFiltered, client))
      }
      .flatMap(_.find(query))
}

object MapAlgebraStacOgcRepositories {
  def algebra: Algebra[QueryF, List[MapAlgebraSourceConf] => List[MapAlgebraSourceConf]] = Algebra {
    case Nothing()        => _ => Nil
    case WithName(name)   => _.filter { _.name == name }
    case WithNames(names) => _.filter { c => names.contains(c.name) }
    case And(e1, e2)      => list => val left = e1(list); left intersect e2(left)
    case Or(e1, e2)       => list => e1(list) ++ e2(list)
    case _                => identity
  }

  def eval(query: Query)(list: List[MapAlgebraSourceConf]): List[MapAlgebraSourceConf] =
    scheme.cata(algebra).apply(query)(list)
}
