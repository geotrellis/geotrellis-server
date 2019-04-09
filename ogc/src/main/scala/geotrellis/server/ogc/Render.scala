package geotrellis.server.ogc

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.render.png._
import geotrellis.raster.histogram._
import geotrellis.raster.render.png._
import scala.util.Try

object Render {
  def apply(mbtile: MultibandTile, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
    maybeStyle match {
      case Some(style) =>
        style.renderImage(mbtile, format, hists)
      case None =>
        format match {
          case format: OutputFormat.Png =>
            format.render(mbtile.band(bandIndex = 0))

          case OutputFormat.Jpg =>
            mbtile.band(bandIndex = 0).renderJpg.bytes

          case OutputFormat.GeoTiff => ??? // Implementation necessary
        }
    }

}
