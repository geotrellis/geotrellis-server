package geotrellis.server

import geotrellis.server.vlm._
import geotrellis.raster._
import geotrellis.raster.geotiff._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.proj4.WebMercator
import geotrellis.vector.Extent

import cats.effect._
import cats.data.{NonEmptyList => NEL}

case class ResourceTile(name: String) {
  def uri = {
    val f = getClass.getResource(s"/$name").getFile
    s"file://$f"
  }
}

object ResourceTile extends RasterSourceUtils {
  def getRasterSource(uri: String): RasterSource = GeoTiffRasterSource(uri)

  implicit val extentReification: ExtentReification[ResourceTile] = new ExtentReification[ResourceTile] {
    def extentReification(self: ResourceTile)(implicit contextShift: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
      (extent: Extent, cs: CellSize) => {
        val rs = getRasterSource(self.uri.toString)
        rs.resample(TargetRegion(new GridExtent[Long](extent, cs)), NearestNeighbor, AutoHigherResolution)
          .read(extent)
          .map { raster => ProjectedRaster(raster, rs.crs) }
          .toIO { new Exception(s"No tile avail for RasterExtent: ${RasterExtent(extent, cs)}") }
      }
  }

  implicit val nodeRasterExtents: HasRasterExtents[ResourceTile] = new HasRasterExtents[ResourceTile] {
    def rasterExtents(self: ResourceTile)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] =
      getRasterExtents(self.uri.toString)
  }

}
