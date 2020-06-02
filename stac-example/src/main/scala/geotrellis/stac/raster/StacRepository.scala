package geotrellis.stac.raster

import geotrellis.raster.RasterSource
import geotrellis.server.ogc.{OgcSource, SimpleSource}
import geotrellis.server.ogc.conf.{MapAlgebraSourceConf, StacSourceConf}
import geotrellis.stac.api.{Http4sStacClient, SearchFilters, StacClient}
import geotrellis.store.query
import geotrellis.store.query._
import cats.effect.{IO, Sync}
import cats.syntax.functor._
import org.http4s.Uri
import org.http4s.client.Client

case class StacRepository[F[_]: Sync](client: StacClient[F]) extends RepositoryM[F, List, RasterSource] {
  def store: F[List[RasterSource]] = find(query.all)
  def find(query: Query): F[List[RasterSource]] =
    client
      .search(SearchFilters.eval(query))
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
      .search(SearchFilters.eval(query))
      .map { _ flatMap { item =>
        item.assets
          .get(stacSourceConf.asset)
          .map(a => stacSourceConf.toLayer(RasterSource(a.href)))
      } }
      .unsafeRunSync()
}

case class MosaicLayerStacOgcRepository(
  mapAlgebraSourceConf: MapAlgebraSourceConf,
  stacSourceConfs: List[StacSourceConf],
  repository: Repository[List, OgcSource]
) extends Repository[List, OgcSource] {
  val names = stacSourceConfs.map(_.name).toSet
  def store: List[OgcSource] = find(query.all)

  def find(query: Query): List[OgcSource] = {
    val sources = names
      .toList
      .map(n => query or withName(n))
      .flatMap(repository.find)
      .collect { case ss @ SimpleSource(_, _, _, _, _, _, _, _) => ss }

    if(sources.nonEmpty) mapAlgebraSourceConf.modelLabel(sources) :: Nil
    else Nil
  }

}

case class StacOgcRepositories(stacLayers: List[StacSourceConf], client: Client[IO]) extends Repository[List, OgcSource] {
  lazy val repos: List[StacOgcRepository] = stacLayers.map { conf => StacOgcRepository(conf, Http4sStacClient[IO](client, Uri.unsafeFromString(conf.source))) }
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}

case class MosaicStacLayerOgcRepositories(mapAlgebraConfLayers: List[MapAlgebraSourceConf], stacLayers: List[StacSourceConf], client: Client[IO]) extends Repository[List, OgcSource] {
  lazy val repos: List[MosaicLayerStacOgcRepository] = mapAlgebraConfLayers.map { conf =>
    val layerNames = conf.listParams(conf.algebra)
    val stacLayersFiltered = stacLayers.filter { l =>
      l.label match {
        case Some(l) => layerNames.contains(l)
        case _ => false
      }
    }
    MosaicLayerStacOgcRepository(conf, stacLayersFiltered, StacOgcRepositories(stacLayersFiltered, client))
  }
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}

case class OgcRepositories(repos: List[Repository[List, OgcSource]]) extends Repository[List, OgcSource] {
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}
