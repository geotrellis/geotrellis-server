package geotrellis.server

import geotrellis.server._
import geotrellis.server.vlm._
import geotrellis.server.vlm.gdal._
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.gdal._
import geotrellis.raster._
import geotrellis.contrib.vlm.TargetRegion
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.vector.Extent

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}

import _root_.io.circe._
import _root_.io.circe.generic.semiauto._
import cats.effect._
import cats.data.{NonEmptyList => NEL}

import java.io._
import java.net.{URI, URL}


case class ResourceTile(name: String) {
  def uri = {
    val f = getClass.getResource(s"/$name").getFile
    s"file://$f"
  }
}

object ResourceTile extends RasterSourceUtils {
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

  implicit val extentReification: ExtentReification[ResourceTile] = new ExtentReification[ResourceTile] {
    def kind(self: ResourceTile): MamlKind = MamlKind.Image
    def extentReification(self: ResourceTile)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[Literal] =
      (extent: Extent, cs: CellSize) => {
        getRasterSource(self.uri.toString)
          .resample(TargetRegion(RasterExtent(extent, cs)), NearestNeighbor)
          .read(extent)
          .map { RasterLit(_) }
          .toIO { new Exception(s"No tile avail for RasterExtent: ${RasterExtent(extent, cs)}") }
      }
  }

  implicit val nodeRasterExtents: HasRasterExtents[ResourceTile] = new HasRasterExtents[ResourceTile] {
    def rasterExtents(self: ResourceTile)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
  }

}


