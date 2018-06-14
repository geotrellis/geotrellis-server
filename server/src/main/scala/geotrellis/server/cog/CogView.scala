package geotrellis.server.cog

import cats._
import cats.implicits._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.histogram._
import geotrellis.raster.resample._
import geotrellis.raster.render._
import geotrellis.raster.render.ColorRamps
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.proj4._
import geotrellis.util.{ ByteReader, FileRangeReader }
import geotrellis.spark.tiling._
import geotrellis.spark.io.http.util.HttpRangeReader

import scala.util.Try
import java.nio.file.Paths
import java.net.URI


object CogView {

  private val TmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  def getByteReader(uri: URI): Option[ByteReader] = {
    uri.getScheme match {
      case "file" | null =>
        Some(FileRangeReader(Paths.get(uri).toFile))

      case "http" | "https" =>
        Some(HttpRangeReader(uri.toURL()))

      case scheme =>
        None
    }
  }

  def fetchCroppedTile(uri: URI, z: Int, x: Int, y: Int, band: Int = 0): Option[Png] = {
    for {
      br <- getByteReader(uri)
      _ <- { println("br", br); Some(1) }
      tiff = try { GeoTiffReader.readMultiband(br, streaming = true) } catch { case t: Throwable => println(t); throw t }
      _ <- { println("tiff", tiff); Some(1) }
      transform = Proj4Transform(tiff.crs, WebMercator)
      _ <- { println("transform", transform); Some(1) }
      inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      _ <- { println("invTransform", inverseTransform); Some(1) }
      tmsTileExtent: Extent = TmsLevels(z).mapTransform.keyToExtent(x, y)
      _ <- { println("tileExtent", tmsTileExtent); Some(1) }
      tmsTileRE = RasterExtent(tmsTileExtent, TmsLevels(z).cellSize)
      _ <- { println("rasterExtent", tmsTileRE); Some(1) }
      tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
      _ <- { println("tiffTileRE", tiffTileRE); Some(1) }
      raster <- Try(tiff.crop(tiffTileRE.extent, tiffTileRE.cellSize, ResampleMethod.DEFAULT, AutoHigherResolution)).toOption
      _ <- { println("raster", raster); Some(1) }
    } yield {
      val hist = raster.tile.bands(band).histogramDouble
      raster.reproject(tmsTileRE, transform, inverseTransform).tile.band(band).renderPng(ColorRamps.Viridis.toColorMap(hist))
    }
  }

}
