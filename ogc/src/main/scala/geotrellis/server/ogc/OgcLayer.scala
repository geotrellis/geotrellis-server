package geotrellis.server.ogc

import geotrellis.server._
import geotrellis.contrib.vlm._
import geotrellis.raster._
import geotrellis.raster.reproject.ReprojectRasterExtent
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.vector.Extent
import geotrellis.proj4.CRS
import com.azavea.maml.ast._
import com.typesafe.scalalogging.LazyLogging

import cats.effect._
import cats.data.{NonEmptyList => NEL}

/**
 * Layer instances are sufficent to produce displayed the end product of 'get map'
 *  requests. They are produced in [[RasterSourcesModel]] from a combination of a WMS 'GetMap'
 *  (or whatever the analogous request in whatever OGC service is being produced) and an instance
 *  of [[OgcSource]]
 */
trait OgcLayer {
  def name: String
  def title: String
  def crs: CRS
  def style: Option[StyleModel]
}

case class SimpleWmsLayer(
  name: String,
  title: String,
  crs: CRS,
  source: RasterSource,
  style: Option[StyleModel]
) extends OgcLayer

object SimpleWmsLayer extends LazyLogging {
  implicit val simpleWmsReification = new ExtentReification[SimpleWmsLayer] {
    def extentReification(self: SimpleWmsLayer)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
      (extent: Extent, cs: CellSize) =>  IO {
        logger.debug(s"attempting to retrieve layer $self at extent $extent with cell size of $cs")
        val raster: Raster[MultibandTile] = self.source
          .reprojectToGrid(self.crs, RasterExtent(extent, cs))
          .read(extent)
          .getOrElse(throw new Exception(s"Unable to retrieve layer $self at extent $extent with cell size of $cs"))
        logger.debug(s"Successfully retrieved layer $self at extent $extent with cell size of $cs")

        ProjectedRaster(raster, self.crs)
      }
  }

  implicit val simpleWmsRasterExtents: HasRasterExtents[SimpleWmsLayer] = new HasRasterExtents[SimpleWmsLayer] {
    def rasterExtents(self: SimpleWmsLayer)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      IO {
        val resolutions = self.source.resolutions.map { ge =>
          ReprojectRasterExtent(ge.toRasterExtent, self.source.crs, self.crs)
        }
        NEL.fromList(resolutions)
          .getOrElse(NEL(ReprojectRasterExtent(self.source.rasterExtent, self.source.crs, self.crs), Nil))
      }
  }
}

case class MapAlgebraWmsLayer(
  name: String,
  title: String,
  crs: CRS,
  parameters: Map[String, SimpleWmsLayer],
  algebra: Expression,
  style: Option[StyleModel]
) extends OgcLayer

case class SimpleWmtsLayer(
  name: String,
  title: String,
  crs: CRS,
  layout: LayoutDefinition,
  source: RasterSource,
  style: Option[StyleModel]
) extends OgcLayer

object SimpleWmtsLayer {
  implicit val simpleWmtsExtentReification = new ExtentReification[SimpleWmtsLayer] {
    def extentReification(self: SimpleWmtsLayer)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
      (extent: Extent, cs: CellSize) =>  IO {
        val raster: Raster[MultibandTile] = self.source
          .reprojectToGrid(self.crs, RasterExtent(extent, cs))
          .read(extent)
          .getOrElse(throw new Exception(s"Unable to retrieve layer $self at extent $extent with cell size of $cs"))

        ProjectedRaster(raster, self.crs)
      }
  }

  implicit val simpleWmtsReification = new TmsReification[SimpleWmtsLayer] {
    def tmsReification(self: SimpleWmtsLayer, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
      (z: Int, x: Int, y: Int) => IO {
        // NOTE: z comes from layout
        val tile = self.source
          .reproject(self.crs, NearestNeighbor)
          .tileToLayout(self.layout, NearestNeighbor)
          .read(SpatialKey(x, y))
          .getOrElse(throw new Exception(s"Unable to retrieve layer $self at XY of ($x, $y)"))

        val extent = self.layout.mapTransform(SpatialKey(x, y))
        ProjectedRaster(tile, extent, self.crs)
      }
  }

  implicit val simpleWmtsRasterExtents: HasRasterExtents[SimpleWmtsLayer] = new HasRasterExtents[SimpleWmtsLayer] {
    def rasterExtents(self: SimpleWmtsLayer)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      IO {
        val resolutions = self.source.resolutions.map { ge =>
          ReprojectRasterExtent(ge.toRasterExtent, self.source.crs, self.crs)
        }
        NEL.fromList(resolutions)
          .getOrElse(NEL(ReprojectRasterExtent(self.source.rasterExtent, self.source.crs, self.crs), Nil))
      }
  }
}

case class MapAlgebraWmtsLayer(
  name: String,
  title: String,
  crs: CRS,
  layout: LayoutDefinition,
  parameters: Map[String, SimpleWmtsLayer],
  algebra: Expression,
  style: Option[StyleModel]
) extends OgcLayer
