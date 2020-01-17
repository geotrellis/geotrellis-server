package geotrellis.server.ogc.conf

import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.store.{GeoTrellisPath, GeoTrellisRasterSource}
import geotrellis.layer._

/**
 * Encodes an expectation that implementing classes be able to realize
 *  a geotrellis-contrib [[RasterSource]].
 */
sealed trait RasterSourceConf {
  def toRasterSource: RasterSource
}

/** An avro-backed (geotrellis) raster source */
case class GeoTrellis(
  catalogUri: String,
  layer: String,
  zoom: Int,
  bandCount: Int
) extends RasterSourceConf {
  def toRasterSource =
    new GeoTrellisRasterSource(GeoTrellisPath(catalogUri, layer, Some(zoom), Some(bandCount)))
}

/** A geotiff (COG) raster source */
case class GeoTiff(
  uri: String
 ) extends RasterSourceConf {
  def toRasterSource =
    GeoTiffRasterSource(uri)
}
