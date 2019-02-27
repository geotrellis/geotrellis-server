package geotrellis.server.vlm.geotiff

import geotrellis.server._
import geotrellis.server.vlm._
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.TargetRegion
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.raster.io.geotiff.AutoHigherResolution

import geotrellis.vector.Extent
import com.azavea.maml.ast.{MamlKind, RasterLit}

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GeoTiffNode(uri: URI, band: Int, celltype: Option[CellType])

object GeoTiffNode extends RasterSourceUtils {
  def getRasterSource(uri: String): GeoTiffRasterSource = GeoTiffRasterSource(uri)

  implicit val cogNodeEncoder: Encoder[GeoTiffNode] = deriveEncoder[GeoTiffNode]
  implicit val cogNodeDecoder: Decoder[GeoTiffNode] = deriveDecoder[GeoTiffNode]

  implicit val cogNodeRasterExtents: HasRasterExtents[GeoTiffNode] = new HasRasterExtents[GeoTiffNode] {
    def rasterExtents(self: GeoTiffNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
  }

  implicit val cogNodeTmsReification: TmsReification[GeoTiffNode] = new TmsReification[GeoTiffNode] {
    def kind(self: GeoTiffNode): MamlKind = MamlKind.Image
    def tmsReification(self: GeoTiffNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] = (z: Int, x: Int, y: Int) => {
      def fetch(xCoord: Int, yCoord: Int) =
        fetchTile(self.uri.toString, z, xCoord, yCoord, WebMercator)
          .map(_.tile)
          .map(_.band(self.band))

      fetch(x, y).map { tile =>
        val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
        ProjectedRaster(MultibandTile(tile), extent, WebMercator)
      }
    }
  }

  implicit val CogNodeExtentReification: ExtentReification[GeoTiffNode] = new ExtentReification[GeoTiffNode] {
    def kind(self: GeoTiffNode): MamlKind = MamlKind.Image
    def extentReification(self: GeoTiffNode)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] = (extent: Extent, cs: CellSize) => {
      getRasterSource(self.uri.toString)
        .resample(TargetRegion(RasterExtent(extent, cs)), NearestNeighbor, AutoHigherResolution)
        .read(extent, self.band :: Nil)
        .map { ProjectedRaster(_, WebMercator) }
        .toIO { new Exception(s"No tile avail for RasterExtent: ${RasterExtent(extent, cs)}") }
    }
  }
}

