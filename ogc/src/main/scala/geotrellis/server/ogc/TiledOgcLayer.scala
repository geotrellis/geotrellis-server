package geotrellis.server.ogc

import geotrellis.server._
import geotrellis.layer._
import geotrellis.raster._
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.vector.Extent
import geotrellis.proj4.CRS
import com.azavea.maml.ast._

import cats.effect._
import cats.data.{NonEmptyList => NEL}

/**
 * Layer instances are sufficent to produce displayed the end product of 'get map'
 *  requests. They are produced in [[RasterSourcesModel]] from a combination of a WMS 'GetMap'
 *  (or whatever the analogous request in whatever OGC service is being produced) and an instance
 *  of [[OgcSource]]
 */
sealed trait TiledOgcLayer {
  def name: String
  def title: String
  def crs: CRS
  def style: Option[OgcStyle]
  def layout: LayoutDefinition
}

case class SimpleTiledOgcLayer(
  name: String,
  title: String,
  crs: CRS,
  layout: LayoutDefinition,
  source: RasterSource,
  style: Option[OgcStyle]
) extends TiledOgcLayer

case class MapAlgebraTiledOgcLayer(
  name: String,
  title: String,
  crs: CRS,
  layout: LayoutDefinition,
  parameters: Map[String, SimpleTiledOgcLayer],
  algebra: Expression,
  style: Option[OgcStyle]
) extends TiledOgcLayer

object SimpleTiledOgcLayer {
  implicit val simpleTiledExtentReification = new ExtentReification[SimpleTiledOgcLayer] {
    def extentReification(self: SimpleTiledOgcLayer)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
      (extent: Extent, cs: CellSize) =>  IO {
        val raster: Raster[MultibandTile] = self.source
          .reprojectToRegion(self.crs, new GridExtent[Long](extent, cs).toRasterExtent)
          .read(extent)
          .getOrElse(throw new Exception(s"Unable to retrieve layer $self at extent $extent with cell size of $cs"))

        ProjectedRaster(raster, self.crs)
      }
  }

  implicit val simpleTiledReification = new TmsReification[SimpleTiledOgcLayer] {
    def tmsReification(self: SimpleTiledOgcLayer, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
      (z: Int, x: Int, y: Int) => IO {
        // NOTE: z comes from layout
        val tile = self.source
          .reproject(self.crs, DefaultTarget)
          .tileToLayout(self.layout, NearestNeighbor)
          .read(SpatialKey(x, y))
          .getOrElse(throw new Exception(s"Unable to retrieve layer $self at XY of ($x, $y)"))

        val extent = self.layout.mapTransform(SpatialKey(x, y))
        ProjectedRaster(tile, extent, self.crs)
      }
  }

  implicit val simpleTiledRasterExtents: HasRasterExtents[SimpleTiledOgcLayer] = new HasRasterExtents[SimpleTiledOgcLayer] {
    def rasterExtents(self: SimpleTiledOgcLayer)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      IO {
        val rasterExtents = self.source.resolutions.map { cs =>
          val re = RasterExtent(self.source.extent ,cs)
          ReprojectRasterExtent(re, self.source.crs, self.crs)
        }

        NEL.fromList(rasterExtents)
          .getOrElse(NEL(self.source.gridExtent.reproject(self.source.crs, self.crs).toRasterExtent, Nil))
      }
  }
}
