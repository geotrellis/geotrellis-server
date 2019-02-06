package geotrellis.server.ogc.conf


import geotrellis.server.ogc.wms.LayerModel
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.avro.GeotrellisRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.spark.LayerId

case class LayerConf(
  name: String,
  title: String,
  source: RasterSourceConf,
  styles: List[StyleConf]
) {
  def model: LayerModel = {
    LayerModel(name, title, source.toRasterSource, styles.map(_.model))
  }
}

sealed trait RasterSourceConf {
  def toRasterSource: RasterSource
}

case class GeoTrellis(
  catalogUri: String,
  layer: String,
  zoom: Int,
  bandCount: Int
) extends RasterSourceConf {
  def toRasterSource =
    GeotrellisRasterSource(catalogUri, LayerId(layer, zoom), bandCount)
}

case class GeoTiff(
  uri: String
 ) extends RasterSourceConf {
  def toRasterSource =
    GeoTiffRasterSource(uri)
}