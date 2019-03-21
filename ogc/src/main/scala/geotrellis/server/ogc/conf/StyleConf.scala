package geotrellis.server.ogc.conf

import geotrellis.server.ogc._

import geotrellis.raster.Tile
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{ColorMap, ColorRamp}
import com.typesafe.config.ConfigFactory
import pureconfig._


sealed trait StyleConf {
  def name: String
  def title: String
  def toStyle: OgcStyle
}

final case class ColorRampConf(
  name: String,
  title: String,
  colors: ColorRamp,
  stops: Option[Int]
) extends StyleConf {
  def toStyle: OgcStyle = ColorRampStyle(name, title, colors, stops)
}

final case class ColorMapConf(
  name: String,
  title: String,
  colorMap: ColorMap
) extends StyleConf {
  def toStyle: OgcStyle = ColorMapStyle(name, title, colorMap)
}
