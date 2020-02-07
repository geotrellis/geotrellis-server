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
  def renderImage(
    mbtile: MultibandTile,
    format: OutputFormat,
    hists: List[Histogram[Double]]
  ): Array[Byte] = {
    val numStops: Int = stops.getOrElse(colorRamp.colors.length)

    val clampedBreaks =
      mbtile.band(0)
        .mapDouble { z =>
          val min = minRender.getOrElse(Double.MinValue)
          val max = maxRender.getOrElse(Double.MaxValue)
          if (isData(z)) { if(z > max) { max } else if(z < min) { min } else { z } }
          else { z }
        }.histogramDouble()
        .quantileBreaks(numStops)

    val interpolatedBreaks =
      Render.linearInterpolationBreaks(clampedBreaks, numStops)

    val cmap = colorRamp.toColorMap(interpolatedBreaks)
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
