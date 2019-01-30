package geotrellis.server.ogc.conf

import java.net.{InetAddress, URL}

case class Conf(
  http: Conf.Http,
  layers: List[Conf.GeoTrellisLayer])

object Conf {

  case class Http(interface: String, port: Int) {
    def asUrl: URL = {
      // TODO: move decision to attach WMS to the point where we decide which service (wms, wmts, wcs) to bind
      if (interface == "0.0.0.0")
        new URL("http", InetAddress.getLocalHost.getHostAddress, port, "/wms")
      else
        new URL("http", interface, port, "/wms")
    }
  }

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
