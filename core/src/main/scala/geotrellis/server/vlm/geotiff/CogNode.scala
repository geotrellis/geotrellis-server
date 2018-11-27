package geotrellis.server.vlm.geotiff

import geotrellis.server._
import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm.geotiff._
import geotrellis.server.vlm.geotiff.util._
import geotrellis.raster._
import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.maml.eval.tile._
import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}
import cats.syntax.all._

import java.net.URI

case class CogNode(uri: URI, band: Int, celltype: Option[CellType])

/** TODO: make it use RasterSources and remove COGUtils at all **/
object CogNode extends RasterSourceUtils {
  def getRasterSource(uri: String): GeoTiffRasterSource = new GeoTiffRasterSource(uri)

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = IO {
    val tiff = getRasterSource(uri).tiff
    NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
  }

  implicit val cogNodeEncoder: Encoder[CogNode] = deriveEncoder[CogNode]
  implicit val cogNodeDecoder: Decoder[CogNode] = deriveDecoder[CogNode]

  implicit val cogNodeRasterExtents: HasRasterExtents[CogNode] = new HasRasterExtents[CogNode] {
    def rasterExtents(self: CogNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
      }
    def crs(self: CogNode)(implicit contextShift: ContextShift[IO]): IO[CRS] =
      CogUtils.getTiff(self.uri.toString).map { tiff =>
        tiff.crs
      }
  }

  implicit val cogNodeTmsReification: TmsReification[CogNode] = new TmsReification[CogNode] {
    def kind(self: CogNode): MamlKind = MamlKind.Image
    def tmsReification(self: CogNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
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

  implicit val cogNodeExtentReification: ExtentReification[CogNode] = new ExtentReification[CogNode] {
    def kind(self: CogNode): MamlKind = MamlKind.Image
    def extentReification(self: CogNode)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] = (extent: Extent, cs: CellSize) => {
      CogUtils.getTiff(self.uri.toString)
        .map { CogUtils.cropGeoTiffToTile(_, extent, cs, self.band) }
        .map { RasterLit(_) }
    }
  }
}

