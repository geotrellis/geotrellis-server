package geotrellis.server.ogc.conf

import geotrellis.server.ogc._

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
  legends: List[LegendModel] = Nil
) extends StyleConf {
  def toStyle: OgcStyle = ColorRampStyle(name, title, colors, stops, legends)
}

/** Styling in which both a color scheme and the data-to-color mapping is known */
final case class ColorMapConf(
  name: String,
  title: String,
  colorMap: ColorMap,
  legends: List[LegendModel] = Nil
) extends StyleConf {
  def toStyle: OgcStyle = ColorMapStyle(name, title, colorMap, legends)
}
