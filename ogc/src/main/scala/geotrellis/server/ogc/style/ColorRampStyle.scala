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

package geotrellis.server.ogc.style

import geotrellis.server.ogc._

import geotrellis.proj4.CRS
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.io.geotiff.GeoTiff
import geotrellis.raster.render.ColorRamp
import geotrellis.util.np.linspace

case class ColorRampStyle(
  name: String,
  title: String,
  colorRamp: ColorRamp,
  stops: Option[Int],
  minRender: Option[Double],
  maxRender: Option[Double],
  clampWithColor: Boolean = false,
  legends: List[LegendModel] = Nil
) extends OgcStyle {
  lazy val logger = org.log4s.getLogger
  def breaks(hists: List[Histogram[Double]], numBreaks: Int): Array[Double] = {
    val minBreak = minRender
      .orElse(hists.head.minValue())
      .getOrElse {
        logger.warn(s"No minimum render value found, using ${Double.MinValue}")
        Double.MinValue
      }
    val maxBreak = maxRender
      .orElse(hists.head.maxValue())
      .getOrElse {
        logger.warn(s"No maximum render value found, using ${Double.MaxValue}")
        Double.MaxValue
      }
    linspace(minBreak, maxBreak, numBreaks)
  }

  def renderRaster(
    raster: Raster[MultibandTile],
    crs: CRS,
    format: OutputFormat,
    hists: List[Histogram[Double]]
  ): Array[Byte] = {
    // The number of stops between each provided break
    val numStops: Int = stops.getOrElse(colorRamp.colors.length)

    // The colors, interpolated (colors added at start and end for out of bounds values)
    val minColor = if (clampWithColor) colorRamp.colors.head else 0x00000000
    val maxColor = if (clampWithColor) colorRamp.colors.last else 0x00000000
    val interpolatedColors: Vector[Int] = minColor +: colorRamp.stops(numStops) :+ maxColor

    val interpolatedBreaks: Array[Double] = breaks(hists, interpolatedColors.length) :+ Double.MaxValue
    val cmap = ColorRamp(interpolatedColors).toColorMap(interpolatedBreaks)

    format match {
      case format: OutputFormat.Png => format.render(raster.tile.band(bandIndex = 0), cmap)
      case OutputFormat.Jpg         => raster.tile.band(bandIndex = 0).renderJpg(cmap).bytes
      case OutputFormat.GeoTiff     => GeoTiff(raster.mapTile(_.band(bandIndex = 0).color(cmap)), crs).toCloudOptimizedByteArray
    }
  }
}
