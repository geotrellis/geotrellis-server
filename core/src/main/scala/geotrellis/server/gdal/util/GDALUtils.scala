package geotrellis.server.gdal.util

import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster.{CellSize, MultibandTile, Raster, RasterExtent}
import geotrellis.raster.resample.{NearestNeighbor, ResampleMethod}
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling.{LayoutDefinition, ZoomedLayoutScheme}

import cats.effect.IO
import cats.data.{NonEmptyList => NEL}

object GDALUtils {
  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def fetch(uri: String, zoom: Int, x: Int, y: Int, crs: CRS = WebMercator, method: ResampleMethod = NearestNeighbor): IO[Raster[MultibandTile]] = {
    val key = SpatialKey(x, y)
    val ld = tmsLevels(zoom)
    val rs = GDALRasterSource(uri).reproject(crs, method).tileToLayout(ld, method)

    rs.read(key) match {
      case Some(t) => IO.pure(Raster(t, ld.mapTransform(key)))
      case _ => IO.raiseError(new Exception(s"No Tile availble for the following SpatialKey: ${x}, ${y}"))
    }
  }

  def getCRS(uri: String): IO[CRS] = IO { GDALRasterSource(uri).crs }

  def getRasterExtents(uri: String): IO[NEL[RasterExtent]] = IO {
    val rs = GDALRasterSource(uri)
    val dataset = rs.dataset
    val band = dataset.GetRasterBand(1)

    NEL(rs.rasterExtent, (0 until band.GetOverviewCount()).toList.map { idx =>
      val ovr = band.GetOverview(idx)
      RasterExtent(rs.extent, CellSize(ovr.GetXSize(), ovr.GetYSize()))
    })
  }
}
