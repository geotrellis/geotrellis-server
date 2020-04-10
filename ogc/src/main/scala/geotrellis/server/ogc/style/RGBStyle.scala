package geotrellis.server.ogc.style

import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram.Histogram
import geotrellis.server.ogc.OutputFormat

case class RGBStyle(name: String, title: String, legends: List[LegendModel] = Nil) extends OgcStyle {
  def renderImage(mbtile: MultibandTile, format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] = {
    format match {
      case _: OutputFormat.Png => mbtile.renderPng()
      case OutputFormat.Jpg => mbtile.renderJpg()
      case OutputFormat.GeoTiff => ??? // Implementation necessary
    }
  }
}