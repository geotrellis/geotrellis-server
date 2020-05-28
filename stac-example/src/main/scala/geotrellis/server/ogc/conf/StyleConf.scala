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

package geotrellis.server.ogc.conf

import geotrellis.server.ogc.style._

import geotrellis.raster.render.{ColorMap, ColorRamp}

/** The trait implemented by different style configuration options */
sealed trait StyleConf {
  def name: String
  def title: String
  def toStyle: OgcStyle
}

/** Styling in which a color scheme is known but not the values that these colors should map to */
final case class ColorRampConf(
  name: String,
  title: String,
  colors: ColorRamp,
  stops: Option[Int],
  minRender: Option[Double],
  maxRender: Option[Double],
  clampWithColor: Boolean = false,
  legends: List[LegendModel] = Nil
) extends StyleConf {
  def toStyle: OgcStyle =
    ColorRampStyle(name, title, colors, stops, minRender, maxRender, clampWithColor, legends)
}

/** Styling in which both a color scheme and the data-to-color mapping is known */
final case class ColorMapConf(
  name: String,
  title: String,
  colorMap: ColorMap,
  legends: List[LegendModel] = Nil
) extends StyleConf {
  def toStyle: OgcStyle =
    ColorMapStyle(name, title, colorMap, legends)
}

final case class InterpolatedColorMapConf(
  name: String,
  title: String,
  colorMap: InterpolatedColorMap,
  legends: List[LegendModel] = Nil
) extends StyleConf {
  def toStyle: OgcStyle =
    InterpolatedColorMapStyle(name, title, colorMap, legends)
}
