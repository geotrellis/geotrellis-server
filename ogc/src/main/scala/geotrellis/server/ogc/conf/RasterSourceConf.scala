package geotrellis.server.ogc.conf

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.avro.GeotrellisRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.spark.LayerId

/** The sumtype responsible for providing a gt-contrib RasterSource from configuration */
sealed trait RasterSourceConf {
  def toRasterSource: RasterSource
}

/** Avro backed layers are referred to in the configuration with this class */
case class GeoTrellis(
  catalogUri: String,
  layer: String,
  zoom: Int,
  bandCount: Int
) extends RasterSourceConf {
  def toRasterSource =
    GeotrellisRasterSource(catalogUri, LayerId(layer, zoom), bandCount)
}

/** GeoTiff/COG backed layers are referred to in the configuration with this class */
case class GeoTiff(
  uri: String
 ) extends RasterSourceConf {
  def toRasterSource =
    GeoTiffRasterSource(uri)
}
