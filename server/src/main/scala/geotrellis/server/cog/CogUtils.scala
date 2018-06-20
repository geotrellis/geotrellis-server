package geotrellis.server.cog

import geotrellis.server.util.{RangeReaderUtils, CacheRangeReader}

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

  private val TmsLevels: Array[LayoutDefinition] = {
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

  def fetch(uri: String, zoom: Int, x: Int, y: Int): IO[MultibandTile] =
    CogUtils.fromUri(uri).flatMap { tiff =>
      val transform = Proj4Transform(tiff.crs, WebMercator)
      val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      val tmsTileRE = RasterExtent(
        extent = TmsLevels(zoom).mapTransform.keyToExtent(x, y),
        cols = 256, rows = 256
      )
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
      val overview = closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))
      cropGeoTiff(overview, tiffTileRE.extent).map { raster =>
        raster.reproject(tmsTileRE, transform, inverseTransform).tile
      }
    }

  def cropForZoomExtent(tiff: GeoTiff[MultibandTile], zoom: Int, extent: Option[Extent]): IO[MultibandTile] = {
    val transform = Proj4Transform(tiff.crs, WebMercator)
    val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
    val actualExtent = extent.getOrElse(tiff.extent.reproject(tiff.crs, WebMercator))
    val tmsTileRE = RasterExtent(extent = actualExtent, cellSize = TmsLevels(zoom).cellSize)
    val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
    val overview = closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))

    cropGeoTiff(overview, tiffTileRE.extent).map { raster =>
      raster.reproject(tmsTileRE, transform, inverseTransform).tile
    }
  }

    /** Work around GeoTiff.closestTiffOverview being private to geotrellis */
  def closestTiffOverview[T <: CellGrid](tiff: GeoTiff[T], cs: CellSize, strategy: OverviewStrategy): GeoTiff[T] = {
    geotrellis.hack.GTHack.closestTiffOverview(tiff, cs, strategy)
  }


  def getTiffExtent(uri: String): IO[Projected[MultiPolygon]] =
    RangeReaderUtils.fromUri(uri).map { rr =>
      val tiff = GeoTiffReader.readMultiband(rr, streaming = true)
      val crs = tiff.crs
      Projected(MultiPolygon(tiff.extent.reproject(crs, WebMercator).toPolygon()), 3857)
    }

  /** Work around bug in GeoTiff.crop(extent) method */
  def cropGeoTiff[T <: CellGrid](tiff: GeoTiff[T], extent: Extent): IO[Raster[T]] = IO {
    if (extent.intersects(tiff.extent)) {
      val bounds = tiff.rasterExtent.gridBoundsFor(extent)
      val clipExtent = tiff.rasterExtent.extentFor(bounds)
      val clip = tiff.crop(List(bounds)).next._2
      Raster(clip, clipExtent)
    } else throw new java.lang.IllegalArgumentException(s"no intersection with geotiff and extent $extent")
  }

  def geoTiffHistogram(tiff: GeoTiff[MultibandTile], buckets: Int = 80, size: Int = 128): Array[StreamingHistogram] = {
    def diagonal(tiff:
                 GeoTiff[MultibandTile]): Int =
      math.sqrt(tiff.cols*tiff.cols + tiff.rows*tiff.rows).toInt

    val goldyLocksOverviews = tiff.overviews.filter{ tiff =>
      val d = diagonal(tiff)
      (d >= size && d <= size*4)
    }

    if (goldyLocksOverviews.nonEmpty){
      // case: overview that is close enough to the size, not more than 4x larger
      // -- read the overview and get histogram
      val theOne = goldyLocksOverviews.minBy(diagonal)
      val hists = Array.fill(tiff.bandCount)(new StreamingHistogram(buckets))
      theOne.tile.foreachDouble{ (band, v) => hists(band).countItem(v, 1) }
      hists
    } else {
      // case: such oveview can't be found
      // -- take min overview and sample window from center
      val theOne = tiff.overviews.minBy(diagonal)
      val sampleBounds = {
        val side = math.sqrt(size*size/2)
        val centerCol = theOne.cols / 2
        val centerRow = theOne.rows / 2
        GridBounds(
          colMin = math.max(0, centerCol - (side / 2)).toInt,
          rowMin = math.max(0, centerRow - (side / 2)).toInt,
          colMax = math.min(theOne.cols - 1, centerCol + (side / 2)).toInt,
          rowMax = math.min(theOne.rows - 1, centerRow + (side / 2)).toInt
        )
      }
      val sample = theOne.crop(List(sampleBounds)).next._2
      val hists = Array.fill(tiff.bandCount)(new StreamingHistogram(buckets))
      sample.foreachDouble{ (band, v) => hists(band).countItem(v, 1) }
      hists
    }
  }
}
