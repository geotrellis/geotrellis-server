package geotrellis.server.ogc.conf

import geotrellis.server.ogc.wms.source._

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.avro.GeotrellisRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.spark.LayerId

sealed trait RasterSourceConf {
  def toRasterSource: RasterSource
}

case class GeoTrellis(
  catalogUri: String,
  source: String,
  zoom: Int,
  bandCount: Int
) extends RasterSourceConf {
  def toRasterSource =
    GeotrellisRasterSource(catalogUri, LayerId(source, zoom), bandCount)
}

case class GeoTiff(
  uri: String
 ) extends RasterSourceConf {
  def toRasterSource =
    GeoTiffRasterSource(uri)
}
