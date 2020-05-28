package geotrellis.stac.raster

// import cats.Id
import cats.effect.Sync
import cats.syntax.functor._
import geotrellis.raster.RasterSource
import geotrellis.stac.api.{Http4sStacClient, SearchFilters}
import geotrellis.store.query
import geotrellis.store.query.{Query, RepositoryM}
import higherkindness.droste.scheme

/*
trait RepositoryM[M[_], G[_], T] {
  def store: M[G[T]]
  def find(query: Query): M[G[T]]
}

trait Repository[G[_], T] extends RepositoryM[Id, G, T]
*/

// ordering can be specified via the constructor?
// it is possible to sort raster sources later
case class StacRepository[F[_]: Sync](client: Http4sStacClient[F]) extends RepositoryM[F, List, RasterSource] {
  def store: F[List[RasterSource]] = find(query.all)
  def find(query: Query): F[List[RasterSource]] =
    client
      .search(scheme.cata(SearchFilters.algebra).apply(query))
      // we can sort it here ?
      .map { _ flatMap { item =>
        item.assets.values.map(StacItemAssetRasterSource(_, item.properties))
      } }
}
