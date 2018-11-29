package geotrellis.server.vlm.geotiff

import geotrellis.server._
import geotrellis.server.vlm._
import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.TargetRegion
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.raster._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GeoTiffNode(uri: URI, band: Int, celltype: Option[CellType])

object GeoTiffNode extends RasterSourceUtils {
  def getRasterSource(uri: String): GeoTiffRasterSource = new GeoTiffRasterSource(uri)

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = IO {
    val tiff = getRasterSource(uri).tiff
    NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
  }

  implicit val cogNodeEncoder: Encoder[GeoTiffNode] = deriveEncoder[GeoTiffNode]
  implicit val cogNodeDecoder: Decoder[GeoTiffNode] = deriveDecoder[GeoTiffNode]

  implicit val cogNodeRasterExtents: HasRasterExtents[GeoTiffNode] = new HasRasterExtents[GeoTiffNode] {
    def rasterExtents(self: GeoTiffNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
    def crs(self: GeoTiffNode)(implicit contextShift: ContextShift[IO]): IO[CRS] =
      getCRS(self.uri.toString)
  }

  implicit val cogNodeTmsReification: TmsReification[GeoTiffNode] = new TmsReification[GeoTiffNode] {
    def kind(self: GeoTiffNode): MamlKind = MamlKind.Image
    def tmsReification(self: GeoTiffNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
      def fetch(xCoord: Int, yCoord: Int) =
        fetchTile(self.uri.toString, z, xCoord, yCoord)
          .map(_.tile)
          .map(_.band(self.band))

      fetch(x, y).map { tile =>
        val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
        RasterLit(Raster(MultibandTile(tile), extent))
      }
    }
  }

  implicit val CogNodeExtentReification: ExtentReification[GeoTiffNode] = new ExtentReification[GeoTiffNode] {
    def kind(self: GeoTiffNode): MamlKind = MamlKind.Image
    def extentReification(self: GeoTiffNode)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] = (extent: Extent, cs: CellSize) => {
      getRasterSource(self.uri.toString)
        .resample(TargetRegion(RasterExtent(extent, cs)), NearestNeighbor)
        .read(extent, self.band :: Nil)
        .map { RasterLit(_) }
        .toIO { new Exception(s"No tile avail for RasterExtent: ${RasterExtent(extent, cs)}") }
    }
  }
}

