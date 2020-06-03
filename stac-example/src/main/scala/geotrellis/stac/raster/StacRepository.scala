package geotrellis.stac.raster

import cats.data.NonEmptyList
import cats.syntax.option._
import geotrellis.raster.{MosaicRasterSource, RasterSource}
import geotrellis.server.ogc.{ OgcSource, SimpleSource }
import geotrellis.server.ogc.conf.{ MapAlgebraSourceConf, StacSourceConf }
import geotrellis.stac.api.{Http4sStacClient, SearchFilters, StacClient}
import geotrellis.store.query
import geotrellis.store.query._
import cats.effect.{IO, Sync}
import cats.syntax.functor._
import higherkindness.droste.{Algebra, scheme}
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
  def find(query: Query): List[OgcSource] = {
    // we need to replace the actual conf name with the stac api layer name
    val filters = SearchFilters.eval(query, stacSourceConf.layer :: Nil)
    val rasterSources = client
      .search(filters)
      .map { _.flatMap { item =>
        item.assets
          .get(stacSourceConf.asset)
          .map(a => RasterSource(a.href))
      }}
      .unsafeRunSync()
    val source: Option[RasterSource] = rasterSources match {
      case head :: Nil => head.some
      case head :: tail =>
        // TODO: Remove and test after https://github.com/locationtech/geotrellis/issues/3248
        //       is addressed. If we let MosaicRasterSource construct it's own gridExtent
        //       it crashes with slightly mismatched Extent for a given CellSize
        val mosaicGridExtent = rasterSources.map(_.gridExtent).reduce(_ combine _)
        MosaicRasterSource(NonEmptyList(head, tail), head.crs, mosaicGridExtent).some
      case _ => None
    }
    source.map(stacSourceConf.toLayer).toList
  }
}

case class MapAlgberaLayerStacOgcRepository(
  mapAlgebraSourceConf: MapAlgebraSourceConf,
  stacSourceConfs: List[StacSourceConf],
  repository: Repository[List, OgcSource]
) extends Repository[List, OgcSource] {
  val names = stacSourceConfs.map(_.name).toSet

  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = {
    // replace the name of the mapalgebra with the name of each layer
    val sources = names
      .toList
      .map(n => query or withName(n)) // TODO: replace it with some other command?
      .flatMap(repository.find)
      .collect { case ss @ SimpleSource(_, _, _, _, _, _, _) => ss }

    mapAlgebraSourceConf.modelOpt(sources).toList
  }
}

case class StacOgcRepositories(stacLayers: List[StacSourceConf], client: Client[IO]) extends Repository[List, OgcSource] {
  lazy val repos: List[StacOgcRepository] = stacLayers.map { conf => StacOgcRepository(conf, Http4sStacClient[IO](client, Uri.unsafeFromString(conf.source))) }
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] =
    StacOgcRepositories.eval(query)(stacLayers)
      .map(conf => StacOgcRepository(conf, Http4sStacClient[IO](client, Uri.unsafeFromString(conf.source))))
      .flatMap(_.find(query))
}

object StacOgcRepositories {
  import geotrellis.store.query._
  import geotrellis.store.query.QueryF._
  def algebra: Algebra[QueryF, List[StacSourceConf] => List[StacSourceConf]] = Algebra {
    case Nothing()        => _ => Nil
    case All()            => identity
    case WithName(name)   => _.filter { _.name == name }
    case WithNames(names) => _.filter { c => names.contains(c.name) }
    case At(_, _)         => identity
    case Between(_, _, _) => identity
    case Intersects(_)    => identity
    case Covers(_)        => identity
    case Contains(_)      => identity
    case And(e1, e2)      => list => val left = e1(list); left intersect e2(left)
    case Or(e1, e2)       => list => e1(list) ++ e2(list)
  }

  def eval(query: Query)(list: List[StacSourceConf]): List[StacSourceConf] =
    scheme.cata(algebra).apply(query)(list)
}

case class MapAlgberaStacLayerOgcRepositories(mapAlgebraConfLayers: List[MapAlgebraSourceConf], stacLayers: List[StacSourceConf], client: Client[IO]) extends Repository[List, OgcSource] {
  lazy val repos: List[MapAlgberaLayerStacOgcRepository] = mapAlgebraConfLayers.map { conf =>
    val layerNames = conf.listParams(conf.algebra)
    val stacLayersFiltered = stacLayers.filter(l => layerNames.contains(l.name))
    MapAlgberaLayerStacOgcRepository(conf, stacLayersFiltered, StacOgcRepositories(stacLayersFiltered, client))
  }
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}

case class OgcRepositories(repos: List[Repository[List, OgcSource]]) extends Repository[List, OgcSource] {
  def store: List[OgcSource] = find(query.all)
  def find(query: Query): List[OgcSource] = repos.flatMap(_.find(query))
}
