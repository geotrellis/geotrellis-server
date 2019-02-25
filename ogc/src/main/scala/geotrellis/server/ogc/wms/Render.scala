package geotrellis.server.ogc.wms

import geotrellis.server.ogc.source._

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.histogram._

object Render {

  private def getLayerColorMap(maybeStyle: Option[StyleModel]): Option[ColorMap] = {
    // TODO: add "default-style" to config file to explicitly select the style
    maybeStyle.flatMap { style =>
      style.colorMap.orElse {
        style.colorRamp.map { colorRamp =>
          val numStops: Int = style.stops.getOrElse(colorRamp.colors.length)
          val ramp: ColorRamp = colorRamp.stops(numStops)
          // TODO: lookup layer histogram, it should be written to LayerId(layerName, 0) at attribute "histogram"
          // GeotrellisRasterSource would have access to attributeStore, we can match and pick
          // for other raster sources it would have to be sampled, stage 2
          // Note: MAML has utility function to sample histograms from RasterSource
          val hist: Array[Histogram[Double]] = ???

          // we're assuming the layers are single band rasters
          ColorMap.fromQuantileBreaks(hist.head, ramp)
        }
      }
    }
  }


  def apply(mbtile: MultibandTile, style: Option[StyleModel], format: Format): Array[Byte] =
    getLayerColorMap(style) match {
      case Some(colorMap) =>
        format match {
          case Format.Png => mbtile.band(bandIndex = 0).renderPng(colorMap).bytes
          case Format.Jpg => mbtile.band(bandIndex = 0).renderJpg(colorMap).bytes
          case Format.GeoTiff => ??? // Implementation necessary
        }
      case None =>
        format match {
          case Format.Png => mbtile.band(bandIndex = 0).renderPng.bytes
          case Format.Jpg => mbtile.band(bandIndex = 0).renderJpg.bytes
          case Format.GeoTiff => ??? // Implementation necessary
        }
    }

}
