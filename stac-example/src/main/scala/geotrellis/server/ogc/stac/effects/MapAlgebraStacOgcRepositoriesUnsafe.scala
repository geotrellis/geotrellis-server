package geotrellis.server.ogc.stac.effects

import cats.effect.ContextShift
import cats.{MonadThrow, Parallel}
import cats.syntax.semigroup._
import geotrellis.raster.effects.UnsafeLift
import geotrellis.server.ogc.conf.{MapAlgebraSourceConf, OgcSourceConf, RasterSourceConf, StacSourceConf}
import geotrellis.server.ogc.stac.{MapAlgebraStacOgcRepository, StacOgcRepositories}
import geotrellis.server.ogc.{OgcSource, OgcSourceRepository}
import geotrellis.store.query
import geotrellis.store.query.{Query, RepositoryM}
import sttp.client3.SttpBackend

case class MapAlgebraStacOgcRepositoriesUnsafe[F[_]: Parallel: UnsafeLift: MonadThrow: ContextShift](
  mapAlgebraConfLayers: List[MapAlgebraSourceConf],
  ogcLayers: List[OgcSourceConf],
  client: SttpBackend[F, Any]
) extends RepositoryM[F, List, OgcSource] {
  def store: F[List[OgcSource]] =
    find(query.withNames(mapAlgebraConfLayers.map(_.name).toSet))

  /** At first, choose stacLayers that fit the query, because after that we'll erase their name.
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
        val ogcLayersFiltered = ogcLayers.filter(l => layerNames.contains(l.name))
        val stacLayers        = ogcLayersFiltered.collect { case ssc: StacSourceConf => ssc }
        val rasterLayers      =
          if (stacLayers.nonEmpty) ogcLayersFiltered.collect { case ssc: RasterSourceConf => ssc.toLayer }
          else Nil
        val repositories      = OgcSourceRepository(rasterLayers).toF[F] |+| StacOgcRepositoriesUnsafe[F](stacLayers, client)

        MapAlgebraStacOgcRepository[F](conf, ogcLayersFiltered, repositories)
      }
      .fold(RepositoryM.empty[F, List, OgcSource])(_ |+| _)
      .find(query)
}
