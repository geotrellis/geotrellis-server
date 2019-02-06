package geotrellis.server.ogc.conf

import geotrellis.raster.Tile
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{ColorMap, ColorRamp}
import geotrellis.server.ogc.wms.StyleModel

sealed trait StyleConf {
  def name: String
  def title: String
  def model: StyleModel
}

final case class ColorRampConf(
  name: String,
  title: String,
  colors: List[String],
  stops: Option[Int]
) extends StyleConf {
  val colorRamp: ColorRamp = {
    val intColors = colors.map(java.lang.Long.decode(_).toInt)
    ColorRamp(intColors.toArray)
  }

  def model: StyleModel =
    StyleModel(name, title, colorRamp = Some(colorRamp), stops = stops)
}

final case class ColorMapConf(
  name: String,
  title: String,
  classes: List[ColorMapping]
) extends StyleConf {
  val colorMap: geotrellis.raster.render.ColorMap = {
    val breaks = classes.map { e =>
      (e.value, java.lang.Long.decode(e.color).toInt)
    }.toMap
    new geotrellis.raster.render.DoubleColorMap(breaks)
  }

  def model =
    StyleModel(name, title, colorMap = Some(colorMap))
}

final case class ColorMapping(value: Double, color: String)