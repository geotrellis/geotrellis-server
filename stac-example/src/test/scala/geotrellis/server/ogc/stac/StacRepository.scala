package geotrellis.server.ogc.stac

import geotrellis.raster.RasterSource
import geotrellis.store.query
import geotrellis.store.query.{Query, RepositoryM}
import com.azavea.stac4s.api.client.{SearchFilters, SttpStacClient}

import cats.syntax.functor._
import cats.syntax.traverse._
import cats.effect.Sync

case class StacRepository[F[_]: Sync](client: SttpStacClient[F]) extends RepositoryM[F, List, RasterSource] {
  def store: F[List[RasterSource]] = find(query.all)

  def find(query: Query): F[List[RasterSource]] =
    SearchFilters
      .eval(query)
      .map { filter =>
        client
          .search(filter)
          .map {
            _ flatMap {
              _.assets.values.map(a => RasterSource(a.href))
            }
          }
      }
      .toList
      .sequence
      .map(_.flatten)
}
