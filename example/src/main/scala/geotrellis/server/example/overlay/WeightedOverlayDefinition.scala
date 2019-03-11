package geotrellis.server.example.overlay

import geotrellis.server._
import HasRasterExtents.ops._
import geotrellis.server.vlm.geotiff.util._

import geotrellis.raster._
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.vector.Extent
import com.azavea.maml.eval.tile._
import _root_.io.circe._
import _root_.io.circe.syntax._
import _root_.io.circe.generic.semiauto._
import cats._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.syntax.all._


case class WeightedOverlayDefinition(uri: String, band: Int, weight: Double)

object WeightedOverlayDefinition {
  implicit val olDecoder: Decoder[WeightedOverlayDefinition] = deriveDecoder
  implicit val olEncoder: Encoder[WeightedOverlayDefinition] = deriveEncoder


  implicit val overlayTmsDefinitionReification: TmsReification[WeightedOverlayDefinition] =
    new TmsReification[WeightedOverlayDefinition] {
      def tmsReification(self: WeightedOverlayDefinition, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) => {
          CogUtils.fetch(self.uri.toString, z, x, y, WebMercator)
            .map(_.tile.band(self.band - 1)).map { tile =>
            val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
            ProjectedRaster(MultibandTile(tile), extent, WebMercator)
          }
        }
    }

  implicit val overlayExtentDefinitionReification: ExtentReification[WeightedOverlayDefinition] =
    new ExtentReification[WeightedOverlayDefinition] {
      def extentReification(self: WeightedOverlayDefinition)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
        (extent: Extent, cs: CellSize) => {
          CogUtils.getTiff(self.uri.toString)
            .map { CogUtils.cropGeoTiffToTile(_, extent, cs, self.band - 1) }
            .map { tile => ProjectedRaster(MultibandTile(tile), extent, WebMercator) }
        }
    }

  implicit val overlayDefinitionRasterExtents: HasRasterExtents[WeightedOverlayDefinition] = new HasRasterExtents[WeightedOverlayDefinition] {
    def rasterExtents(self: WeightedOverlayDefinition)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
      }
    def crs(self: WeightedOverlayDefinition)(implicit contextShift: ContextShift[IO]): IO[CRS] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        tiff.crs
      }
  }
}

