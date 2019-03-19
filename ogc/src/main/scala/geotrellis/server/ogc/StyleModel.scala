package geotrellis.server.ogc

import geotrellis.raster.render.{ColorMap, ColorRamp}

case class StyleModel(
  name: String,
  title: String,
  colorMap: Option[ColorMap] = None,
  colorRamp: Option[ColorRamp] = None,
  stops: Option[Int] = None
) {
  require(colorRamp.isDefined || colorMap.isDefined, "Either colorRamp or colorMap must be defined")
}
