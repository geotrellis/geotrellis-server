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

import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{ColorMap, ColorRamp}
import geotrellis.util.np.linspace

trait OgcStyle {
  def name: String
  def title: String
  def legends: List[LegendModel]
  def renderImage(mbtile: MultibandTile, format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte]
}

case class ColorMapStyle(
  name: String,
  title: String,
  colorMap: ColorMap,
  legends: List[LegendModel] = Nil
) extends OgcStyle {
  def renderImage(
    mbtile: MultibandTile,
    format: OutputFormat,
    hists: List[Histogram[Double]]
  ): Array[Byte] = {
    format match {
      case format: OutputFormat.Png =>
        format.render(mbtile.band(bandIndex = 0), colorMap)

      case OutputFormat.Jpg =>
        mbtile.band(bandIndex = 0).renderJpg(colorMap).bytes

      case OutputFormat.GeoTiff => ??? // Implementation necessary
    }
  }
}

case class ColorRampStyle(
  name: String,
  title: String,
  colorRamp: ColorRamp,
  stops: Option[Int],
  minRender: Option[Double],
  maxRender: Option[Double],
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

  def renderImage(
    mbtile: MultibandTile,
    format: OutputFormat,
    hists: List[Histogram[Double]]
  ): Array[Byte] = {
    // The number of stops between each provided break
    val numStops: Int = stops.getOrElse(colorRamp.colors.length)

    // The colors, interpolated (invisible added for values below our minimum break)
    val interpolatedColors: Vector[Int] =
      0x00000000 +: colorRamp.stops(numStops)

    val interpolatedBreaks: Array[Double] =
      breaks(hists, interpolatedColors.length)

    val cmap = ColorRamp(interpolatedColors).toColorMap(interpolatedBreaks)

    format match {
      case format: OutputFormat.Png =>
       format.render(mbtile.band(bandIndex = 0), cmap)
      case OutputFormat.Jpg =>
        mbtile.band(bandIndex = 0).renderJpg(cmap).bytes
      case OutputFormat.GeoTiff => ??? // Implementation necessary
    }
  }
}

case class LegendModel(
  format: String,
  width: Int,
  height: Int,
  onlineResource: OnlineResourceModel
)

case class OnlineResourceModel(
  `type`: String,
  href: String,
  role: Option[String] = None,
  title: Option[String] = None,
  show: Option[String] = None,
  actuate: Option[String] = None
)
