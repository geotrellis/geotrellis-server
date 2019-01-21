package geotrellis.server.ogc.conf

import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource

case class Conf(http: Conf.Http)

object Conf {
  case class Http(interface: String, port: Int, backend: String) {
    def rasterSource(uri: String): RasterSource = backend match {
      case "GDAL" => GDALRasterSource(uri)
      case _ => GeoTiffRasterSource(uri)
    }
  }

  lazy val conf: Conf = pureconfig.loadConfigOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf
}
