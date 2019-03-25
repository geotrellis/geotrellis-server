package geotrellis.server.ogc.conf

import geotrellis.server.ogc._

import geotrellis.raster.render.{ColorMap, ColorRamp}

sealed trait StyleConf {
  def name: String
  def title: String
  def toStyle: OgcStyle
}

final case class ColorRampConf(
  name: String,
  title: String,
  colors: ColorRamp,
  stops: Option[Int],
  legends: List[LegendModel] = Nil
) extends StyleConf {
  def toStyle: OgcStyle = ColorRampStyle(name, title, colors, stops, legends)
}

final case class ColorMapConf(
  name: String,
  title: String,
  colorMap: ColorMap,
  legends: List[LegendModel] = Nil
) extends StyleConf {
  def toStyle: OgcStyle = ColorMapStyle(name, title, colorMap, legends)
}
