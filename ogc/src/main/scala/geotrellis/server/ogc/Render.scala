package geotrellis.server.ogc

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.histogram._

/** Render an image given an ogc style and fallback to a default rendering strategy without one */
object Render {
  def apply(mbtile: MultibandTile, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
    maybeStyle match {
      case Some(style) =>
        style.renderImage(mbtile, format, hists)
      case None =>
        format match {
          case OutputFormat.Png => mbtile.band(bandIndex = 0).renderPng.bytes
          case OutputFormat.Jpg => mbtile.band(bandIndex = 0).renderJpg.bytes
          case OutputFormat.GeoTiff => ??? // Implementation necessary
        }
    }
}
