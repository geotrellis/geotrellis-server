package geotrellis.server.gdal.vlm

import cats.effect.IO
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster.{MultibandTile, Raster}
import geotrellis.raster.resample.{Bilinear, NearestNeighbor, ResampleMethod}
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling.{LayoutDefinition, ZoomedLayoutScheme}

object GDALUtils {
  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def fetch(uri: String, zoom: Int, x: Int, y: Int, crs: CRS = WebMercator, method: ResampleMethod = Bilinear): IO[Raster[MultibandTile]] = {
    val key = SpatialKey(x, y)
    val ld = tmsLevels(zoom)
    val rs = GDALRasterSource(uri).reproject(crs, method).tileToLayout(ld, method)

    AnyRef.synchronized { rs.read(key) } match {
      case Some(t) => IO.pure(Raster(t, ld.mapTransform(key)))
      case _ => IO.raiseError(new Exception(s"No Tile availble for the following SpatialKey: ${x}, ${y}"))
    }
  }
}
