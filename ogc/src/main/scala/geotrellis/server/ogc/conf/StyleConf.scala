package geotrellis.server.ogc.conf

import geotrellis.raster.Tile
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.render.{ColorMap, ColorRamp}


sealed trait StyleModel {
  def name: String
  def title: String
}

final case class ColorRampStyle(
  name: String,
  title: String,
  colors: List[String]
) extends StyleModel {
  val colorRamp: ColorRamp = {
    val intColors = colors.map(java.lang.Long.decode(_).toInt)
    ColorRamp(intColors.toArray)
  }

  def styleColorMap(hist: Histogram[Double]): ColorMap =
    ColorMap.fromQuantileBreaks(hist, colorRamp)
}

final case class ColorMapStyle(
  name: String,
  title: String,
  map: List[ColorMapping]
) extends StyleModel {
  val colorMap: geotrellis.raster.render.ColorMap = {
    val breaks = map.map { e =>
      (e.value, java.lang.Long.decode(e.color).toInt)
    }.toMap
    new geotrellis.raster.render.DoubleColorMap(breaks)
  }

  def styleColorMap(): ColorMap = colorMap
}

final case class ColorMapping(value: Double, color: String)