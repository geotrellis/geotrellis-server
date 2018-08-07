package geotrellis.server.core.cog

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.resample._
import geotrellis.raster.histogram._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.util._
import geotrellis.proj4._
import geotrellis.spark.tiling._

import scala.concurrent._
import cats.effect.IO
import cats.data._
import cats.implicits._


object CogUtils {

  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  /** Read GeoTiff from URI while caching the header bytes in memcache */
  def fromUri(uri: String): IO[GeoTiff[MultibandTile]] = {
    val cacheSize = 1<<18
    for {
      headerBytes <- RangeReaderUtils.fromUri(uri).map(_.readRange(0, cacheSize))
      rr          <- RangeReaderUtils.fromUri(uri)
    } yield {
      val crr = CacheRangeReader(rr, headerBytes)
      GeoTiffReader.readMultiband(crr, streaming = true)
    }
  }

  def fetch(uri: String, extent: Extent): IO[Raster[MultibandTile]] =
    fromUri(uri).map { tiff => tiff.crop(RasterExtent(extent, tiff.cellSize)) }

  def fetch(uri: String, zoom: Int, x: Int, y: Int): IO[Raster[MultibandTile]] =
    CogUtils.fromUri(uri).flatMap { tiff =>
      val transform = Proj4Transform(tiff.crs, WebMercator)
      val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      val tmsTileRE = RasterExtent(
        extent = tmsLevels(zoom).mapTransform.keyToExtent(x, y),
        cols = 256, rows = 256
      )
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
      val overview = closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))

      cropGeoTiff(overview, tiffTileRE.extent).map { raster =>
        raster.reproject(tmsTileRE, transform, inverseTransform)
      }
    }

    /** Work around GeoTiff.closestTiffOverview being private to geotrellis */
  def closestTiffOverview[T <: CellGrid](tiff: GeoTiff[T], cs: CellSize, strategy: OverviewStrategy): GeoTiff[T] = {
    geotrellis.hack.GTHack.closestTiffOverview(tiff, cs, strategy)
  }


  def getTiff(uri: String): IO[GeoTiff[MultibandTile]] =
    RangeReaderUtils.fromUri(uri).map { rr =>
      GeoTiffReader.readMultiband(rr, streaming = true)
    }

  def cropGeoTiff[T <: CellGrid](tiff: GeoTiff[T], extent: Extent): IO[Raster[T]] = IO {
    if (extent.intersects(tiff.extent)) {
      val bounds = tiff.rasterExtent.gridBoundsFor(extent)
      val clipExtent = tiff.rasterExtent.extentFor(bounds)
      val clip = tiff.crop(List(bounds)).next._2
      Raster(clip, clipExtent)
    } else {
      import geotrellis.vector.io._
      throw new java.lang.IllegalArgumentException(s"no intersection with geotiff extent (${tiff.extent.toPolygon.toGeoJson}) and extent (${extent.toPolygon.toGeoJson})")
    }
  }

  def cropGeoTiffToTile(tiff: GeoTiff[MultibandTile], extent: Extent, cs: CellSize, targetBand: Int): Tile =
    tiff
      .getClosestOverview(cs, Auto(0))
      .crop(extent, Crop.Options(clamp = false))
      .tile
      .band(targetBand)
      .resample(extent, RasterExtent(extent, cs))


}



