package geotrellis.stac.raster

// import cats.Id
import cats.Id
import cats.effect.{IO, Sync}
import cats.syntax.functor._
import geotrellis.raster.RasterSource
import geotrellis.server.ogc.OgcSource
import geotrellis.server.ogc.conf.StacSourceConf
import geotrellis.stac.api.{Http4sStacClient, SearchFilters}
import geotrellis.store.query
import geotrellis.store.query.{Query, Repository, RepositoryM}
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

case class StacOgcRepository(
  client: Http4sStacClient[IO],
  stacSourceConf: StacSourceConf
) extends Repository[List, OgcSource] {
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] =
    client
      .search(scheme.cata(SearchFilters.algebra).apply(query))
      // we can sort it here ?
      .map { _ flatMap { item =>
        item.assets.values.map(StacItemAssetRasterSource(_, item.properties))
      } }
      .unsafeRunSync()
      .map(stacSourceConf.fromRasterSource)

}

case class StacOgcRepositories(stacLayers: List[StacSourceConf]) extends Repository[List, OgcSource] {
  val repos: List[StacOgcRepository] = stacLayers.map(conf => StacOgcRepository(null, conf))
  def store: List[OgcSource] = find(query.all)
  // parallel traverse!
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}

case class OgcRepositories(repos: List[Repository[List, OgcSource]]) extends Repository[List, OgcSource] {
  def store: List[OgcSource] = find(query.all)
  // parallel traverse!
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}
