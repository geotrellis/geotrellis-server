package geotrellis.server.ogc

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.render.png._
import geotrellis.raster.histogram._
import geotrellis.raster.render.png._

import scala.collection.mutable
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

  def linearInterpolationBreaks(breaks: Array[Double], numStops: Int): Array[Double] = {
    val length = breaks.length
    val lengthBetween = numStops.toDouble / length // number of colors between each edge

    val listBuffer: mutable.ListBuffer[Double] = mutable.ListBuffer()
    var counter = 0
    while (counter < (length - 1)) {
      val (currentEdge, nextEdge) = breaks(counter) -> breaks(counter + 1)
      val points = currentEdge to nextEdge by (nextEdge - currentEdge) / lengthBetween

      val append = if (counter == 0) points else points.tail
      listBuffer ++= append

      counter = counter + 1
    }

    listBuffer.toArray
  }
}
