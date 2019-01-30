package geotrellis.server.ogc.conf

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import java.net.URI

case class Conf(
  http: Conf.Http,
  layers: List[Conf.GeoTrellisLayer])

object Conf {
  case class Http(interface: String, port: Int)

  sealed trait Layer

  /**
    * @param catalogUri  URI of the GeoTrellis catalog
    * @param name name of the layer, correct zoom level will be auto selected
    * @param zoom zoom of base layer
    * @param bandCount number of bands contained for each tile in the layer
    */
  case class GeoTrellisLayer(catalogUri: String, name: String, zoom: Int, bandCount: Int = 1) extends Layer

  case class GeoTiffLayer(geoTiffUri: String, name: String) extends Layer

  lazy val conf: Conf = pureconfig.loadConfigOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf
}
