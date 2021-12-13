/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.server.vlm.geotiff.util

import geotrellis.vector._
import geotrellis.layer._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.proj4._

import cats.effect.IO

// TODO: remove this object
object CogUtils {

  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  /** Read GeoTiff from URI while caching the header bytes in memcache */
  def fromUri(uri: String): IO[GeoTiff[MultibandTile]] = {
    val cacheSize = 1 << 18
    for {
      headerBytes <- RangeReaderUtils.fromUri(uri).map(_.readRange(0, cacheSize))
      rr          <- RangeReaderUtils.fromUri(uri)
    } yield {
      val crr = CacheRangeReader(rr, headerBytes)
      GeoTiffReader.readMultiband(crr, streaming = true)
    }
  }

  def fetch(uri: String, extent: Extent): IO[Raster[MultibandTile]] =
    fromUri(uri).map(tiff => tiff.crop(RasterExtent(extent, tiff.cellSize)))

  def fetch(uri: String, zoom: Int, x: Int, y: Int, crs: CRS = WebMercator): IO[Raster[MultibandTile]] =
    CogUtils.fromUri(uri).flatMap { tiff =>
      val transform        = Proj4Transform(tiff.crs, crs)
      val inverseTransform = Proj4Transform(crs, tiff.crs)
      val tmsTileRE = RasterExtent(
        extent = tmsLevels(zoom).mapTransform.keyToExtent(x, y),
        cols = 256,
        rows = 256
      )
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
      val overview   = tiff.getClosestOverview(tiffTileRE.cellSize, Auto(0))

      cropGeoTiff(overview, tiffTileRE.extent).map { raster =>
        raster.reproject(tmsTileRE, transform, inverseTransform)
      }
    }

  def getTiff(uri: String): IO[GeoTiff[MultibandTile]] =
    RangeReaderUtils.fromUri(uri).map { rr =>
      GeoTiffReader.readMultiband(rr, streaming = true)
    }

  def cropGeoTiff[T <: CellGrid[Int]](tiff: GeoTiff[T], extent: Extent): IO[Raster[T]] =
    IO {
      if (extent.intersects(tiff.extent)) {
        val bounds     = tiff.rasterExtent.gridBoundsFor(extent)
        val clipExtent = tiff.rasterExtent.extentFor(bounds)
        val clip       = tiff.crop(List(bounds)).next._2
        Raster(clip, clipExtent)
      } else {
        throw new java.lang.IllegalArgumentException(
          s"no intersection with geotiff extent (${tiff.extent.toPolygon.toGeoJson}) and extent (${extent.toPolygon.toGeoJson})"
        )
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
