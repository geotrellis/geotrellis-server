package geotrellis.stac.raster

import cats.effect.Sync
import cats.syntax.functor._
import geotrellis.raster.RasterSource
import geotrellis.stac.api.{SearchFilters, StacClient}
import geotrellis.store.query
import geotrellis.store.query.{Query, RepositoryM}

case class StacRepository[F[_]: Sync](client: StacClient[F]) extends RepositoryM[F, List, RasterSource] {
  def store: F[List[RasterSource]] = find(query.all)
  def find(query: Query): F[List[RasterSource]] =
    client
      .search(SearchFilters.eval(query))
      .map { _ flatMap { item =>
        item.assets.values.map(a => RasterSource(a.href))
      } }
}
