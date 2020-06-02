package geotrellis.stac.raster

import geotrellis.raster.RasterSource
import geotrellis.server.ogc.OgcSource
import geotrellis.server.ogc.conf.StacSourceConf
import geotrellis.stac.api.{Http4sStacClient, SearchFilters, StacClient}
import geotrellis.store.query
import geotrellis.store.query.{Query, Repository, RepositoryM}
import cats.effect.{IO, Sync}
import cats.syntax.functor._
import higherkindness.droste.scheme
import org.http4s.Uri
import org.http4s.client.Client

case class StacRepository[F[_]: Sync](client: StacClient[F]) extends RepositoryM[F, List, RasterSource] {
  def store: F[List[RasterSource]] = find(query.all)
  def find(query: Query): F[List[RasterSource]] =
    client
      .search(scheme.cata(SearchFilters.algebra).apply(query))
      .map { _ flatMap { item =>
        item.assets.values.map(a => RasterSource(a.href))
      } }
}

case class StacOgcRepository(
  stacSourceConf: StacSourceConf,
  client: StacClient[IO]
) extends Repository[List, OgcSource] {
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] =
    client
      .search(scheme.cata(SearchFilters.algebra).apply(query))
      .map { _ flatMap { item =>
        item.assets
          .get(stacSourceConf.asset)
          .map(a => RasterSource(a.href))
      } }
    .unsafeRunSync()
    .map(stacSourceConf.fromRasterSource)
}

case class StacOgcRepositories(stacLayers: List[StacSourceConf], client: Client[IO]) extends Repository[List, OgcSource] {
  lazy val repos: List[StacOgcRepository] = stacLayers.map { conf => StacOgcRepository(conf, Http4sStacClient[IO](client, Uri.unsafeFromString(conf.source))) }
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}

case class OgcRepositories(repos: List[Repository[List, OgcSource]]) extends Repository[List, OgcSource] {
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}
