package geotrellis.server.example.overlay

import geotrellis.server.core.maml.reification._
import geotrellis.server.core.maml.metadata._
import HasRasterExtents.ops._
import geotrellis.server.core.cog.CogUtils

import com.azavea.maml.ast.{Expression, Literal, MamlKind, RasterLit}
import com.azavea.maml.eval.tile._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import cats._
import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.syntax.all._
import geotrellis.raster._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent


case class OverlayDefinition(uri: String, band: Int, weight: Double)

object OverlayDefinition {
  implicit val olDecoder: Decoder[OverlayDefinition] = deriveDecoder
  implicit val olEncoder: Encoder[OverlayDefinition] = deriveEncoder


  implicit val overlayTmsDefinitionReification: MamlTmsReification[OverlayDefinition] =
    new MamlTmsReification[OverlayDefinition] {
      def kind(self: OverlayDefinition): MamlKind = MamlKind.Tile

      def tmsReification(self: OverlayDefinition, buffer: Int)(implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          CogUtils.fetch(self.uri.toString, z, x, y).map(_.band(self.band - 1)).map { tile =>
            val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
            RasterLit(Raster(tile, extent))
          }
        }
    }

  implicit val overlayExtentDefinitionReification: MamlExtentReification[OverlayDefinition] =
    new MamlExtentReification[OverlayDefinition] {
      def kind(self: OverlayDefinition): MamlKind = MamlKind.Tile

      def extentReification(self: OverlayDefinition)(implicit t: Timer[IO]): (Extent, CellSize) => IO[Literal] =
        (extent: Extent, cs: CellSize) => {
          CogUtils.getTiff(self.uri.toString)
            .map { CogUtils.cropGeoTiffToTile(_, extent, cs, self.band - 1) }
            .map { tile => RasterLit(Raster(tile, extent)) }
        }
    }

  implicit val overlayDefinitionRasterExtents: HasRasterExtents[OverlayDefinition] = new HasRasterExtents[OverlayDefinition] {
    def rasterExtents(self: OverlayDefinition)(implicit t: Timer[IO]): IO[NEL[RasterExtent]] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
      }
    def crs(self: OverlayDefinition)(implicit t: Timer[IO]): IO[CRS] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        tiff.crs
      }
  }
}

