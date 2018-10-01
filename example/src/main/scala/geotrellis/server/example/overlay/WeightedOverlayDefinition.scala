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


case class WeightedOverlayDefinition(uri: String, band: Int, weight: Double)

object WeightedOverlayDefinition {
  implicit val olDecoder: Decoder[WeightedOverlayDefinition] = deriveDecoder
  implicit val olEncoder: Encoder[WeightedOverlayDefinition] = deriveEncoder


  implicit val overlayTmsDefinitionReification: MamlTmsReification[WeightedOverlayDefinition] =
    new MamlTmsReification[WeightedOverlayDefinition] {
      def kind(self: WeightedOverlayDefinition): MamlKind = MamlKind.Tile

      def tmsReification(self: WeightedOverlayDefinition, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          CogUtils.fetch(self.uri.toString, z, x, y).map(_.tile.band(self.band - 1)).map { tile =>
            val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
            RasterLit(Raster(tile, extent))
          }
        }
    }

  implicit val overlayExtentDefinitionReification: MamlExtentReification[WeightedOverlayDefinition] =
    new MamlExtentReification[WeightedOverlayDefinition] {
      def kind(self: WeightedOverlayDefinition): MamlKind = MamlKind.Tile

      def extentReification(self: WeightedOverlayDefinition)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] =
        (extent: Extent, cs: CellSize) => {
          CogUtils.getTiff(self.uri.toString)
            .map { CogUtils.cropGeoTiffToTile(_, extent, cs, self.band - 1) }
            .map { tile => RasterLit(Raster(tile, extent)) }
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

