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

import cats.effect.Sync
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.instances.list._
import com.azavea.stac4s.api.client.SttpStacClient
import geotrellis.raster.RasterSource
import geotrellis.server.ogc.stac.SearchFiltersOps
import geotrellis.store.query
import geotrellis.store.query.{Query, RepositoryM}

case class StacRepository[F[_]: Sync](client: SttpStacClient[F]) extends RepositoryM[F, List, RasterSource] {
  def store: F[List[RasterSource]]              = find(query.all)
  def find(query: Query): F[List[RasterSource]] =
    SearchFiltersOps
      .eval(query)
      .map { filter =>
        client
          .search(filter)
          .map { _ flatMap { _.assets.values.map(a => RasterSource(a.href)) } }
      }
      .toList
      .sequence
      .map(_.flatten)
}
