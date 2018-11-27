package geotrellis.server.vlm.gdal

import geotrellis.server._
import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm.gdal.{GDALBaseRasterSource, GDALRasterSource}
import geotrellis.raster._
import geotrellis.proj4.CRS
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

import java.net.URI

case class GDALNode(uri: URI, band: Int, celltype: Option[CellType])

object GDALNode extends RasterSourceUtils {
  def getRasterSource(uri: String): GDALBaseRasterSource = GDALRasterSource(uri)

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = IO {
    val rs = getRasterSource(uri)
    val dataset = rs.dataset
    val band = dataset.GetRasterBand(1)

    NEL(rs.rasterExtent, (0 until band.GetOverviewCount()).toList.map { idx =>
      val ovr = band.GetOverview(idx)
      RasterExtent(rs.extent, CellSize(ovr.GetXSize(), ovr.GetYSize()))
    })
  }

  implicit val gdalNodeEncoder: Encoder[GDALNode] = deriveEncoder[GDALNode]
  implicit val gdalNodeDecoder: Decoder[GDALNode] = deriveDecoder[GDALNode]

  implicit val GDALNodeRasterExtents: HasRasterExtents[GDALNode] = new HasRasterExtents[GDALNode] {
    def rasterExtents(self: GDALNode)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
    def crs(self: GDALNode)(implicit contextShift: ContextShift[IO]): IO[CRS] = getCRS(self.uri.toString)
  }

  implicit val GDALNodeTmsReification: TmsReification[GDALNode] = new TmsReification[GDALNode] {
    def kind(self: GDALNode): MamlKind = MamlKind.Image
    def tmsReification(self: GDALNode, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
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
}
