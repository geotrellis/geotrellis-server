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

package geotrellis.server.ogc

import geotrellis.server.ogc.style._

import geotrellis.proj4.CRS
import geotrellis.raster._
import geotrellis.raster.io.geotiff.tags.codes.ColorSpace
import geotrellis.raster.io.geotiff.{GeoTiff, GeoTiffOptions, MultibandGeoTiff}
import geotrellis.raster.render.png._
import cats.syntax.option._

object Render {
  def rgb(raster: Raster[MultibandTile], crs: CRS, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
    maybeStyle match {
      case Some(style) => style.renderRaster(raster, crs, format, hists)
      case None =>
        format match {
          case format: OutputFormat.Png => OutputFormat.Png(Some(RgbPngEncoding)).render(raster.tile.color())
          case OutputFormat.Jpg         => raster.tile.color().renderJpg.bytes
          case OutputFormat.GeoTiff     => MultibandGeoTiff(raster, crs, GeoTiffOptions(colorSpace = ColorSpace.RGB)).toCloudOptimizedByteArray
        }
    }

  def rgba(raster: Raster[MultibandTile], crs: CRS, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
    maybeStyle match {
      case Some(style) => style.renderRaster(raster, crs, format, hists)
      case None =>
        format match {
          case format: OutputFormat.Png => OutputFormat.Png(Some(RgbaPngEncoding)).render(raster.tile.withNoData(0d.some).color())
          case OutputFormat.Jpg         => raster.tile.color().renderJpg.bytes
          case OutputFormat.GeoTiff     => MultibandGeoTiff(raster, crs, GeoTiffOptions(colorSpace = ColorSpace.RGB)).toCloudOptimizedByteArray
        }
    }

  def singleband(
      raster: Raster[MultibandTile],
      crs: CRS,
      maybeStyle: Option[OgcStyle],
      format: OutputFormat,
      hists: List[Histogram[Double]]
  ): Array[Byte] =
    maybeStyle match {
      case Some(style) => style.renderRaster(raster, crs, format, hists)
      case None =>
        format match {
          case format: OutputFormat.Png => format.render(raster.tile.band(bandIndex = 0))
          case OutputFormat.Jpg         => raster.tile.band(bandIndex = 0).renderJpg.bytes
          case OutputFormat.GeoTiff     => GeoTiff(raster.mapTile(_.band(bandIndex = 0)), crs).toCloudOptimizedByteArray
        }
    }

  def multiband(
      raster: Raster[MultibandTile],
      crs: CRS,
      maybeStyle: Option[OgcStyle],
      format: OutputFormat,
      hists: List[Histogram[Double]]
  ): Array[Byte] = rgba(raster, crs, maybeStyle, format, hists)
}
