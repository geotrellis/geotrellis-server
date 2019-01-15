package geotrellis.server.example.overlay

import geotrellis.server._
import HasRasterExtents.ops._
import geotrellis.server.vlm.geotiff.util._

import com.azavea.maml.ast.{Expression, Literal, MamlKind, RasterLit}
import com.azavea.maml.eval.tile._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.implicits._
import geotrellis.raster._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent


case class WeightedOverlayDefinition(uri: String, band: Int, weight: Double)

object WeightedOverlayDefinition {
  implicit val olDecoder: Decoder[WeightedOverlayDefinition] = deriveDecoder
  implicit val olEncoder: Encoder[WeightedOverlayDefinition] = deriveEncoder


  implicit val overlayTmsDefinitionReification: TmsReification[WeightedOverlayDefinition] =
    new TmsReification[WeightedOverlayDefinition] {
      def kind(self: WeightedOverlayDefinition): MamlKind = MamlKind.Image

      def tmsReification[F[_]](self: WeightedOverlayDefinition, buffer: Int)(implicit F: ConcurrentEffect[F]): (Int, Int, Int) => F[Literal] =
        (z: Int, x: Int, y: Int) => {
          CogUtils.fetch(self.uri.toString, z, x, y).map(_.tile.band(self.band - 1)).map { tile =>
            val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
            RasterLit(Raster(tile, extent))
          }
        }
    }

  implicit val overlayExtentDefinitionReification: ExtentReification[WeightedOverlayDefinition] =
    new ExtentReification[WeightedOverlayDefinition] {
      def kind(self: WeightedOverlayDefinition): MamlKind = MamlKind.Image

      def extentReification[F[_]](self: WeightedOverlayDefinition)(implicit F: ConcurrentEffect[F]): (Extent, CellSize) => F[Literal] =
        (extent: Extent, cs: CellSize) => {
          CogUtils.getTiff(self.uri.toString)
            .map { CogUtils.cropGeoTiffToTile(_, extent, cs, self.band - 1) }
            .map { tile => RasterLit(Raster(tile, extent)) }
        }
    }

  implicit val overlayDefinitionRasterExtents: HasRasterExtents[WeightedOverlayDefinition] = new HasRasterExtents[WeightedOverlayDefinition] {
    def rasterExtents[F[_]](self: WeightedOverlayDefinition)(implicit F: ConcurrentEffect[F]): F[NEL[RasterExtent]] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
      }
    def crs[F[_]](self: WeightedOverlayDefinition)(implicit F: ConcurrentEffect[F]): F[CRS] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        tiff.crs
      }
  }
}

