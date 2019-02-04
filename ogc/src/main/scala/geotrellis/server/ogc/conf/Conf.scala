package geotrellis.server.ogc.conf

import java.net.{InetAddress, URL}

case class Conf(
  http: Conf.Http,
  service: Conf.Service,
  layers: List[Conf.GeoTrellisLayer],
  styles: Map[String, StyleModel]
) {
    def serviceUrl: URL = {
      // TODO: move decision to attach WMS to the point where we decide which service (wms, wmts, wcs) to bind
      service.url.getOrElse(
        if (http.interface == "0.0.0.0")
          new URL("http", InetAddress.getLocalHost.getHostAddress, http.port, "/wms")
        else
          new URL("http", http.interface, http.port, "/wms")
      )
    }
}

object Conf {

  /** Public URL for this service that will be reported.
    * This may need to be set externall due to containerization or proxies.
    */
  case class Service(url: Option[URL])

  /** Local interface and port binding for the service */
  case class Http(interface: String, port: Int)

  sealed trait Layer {
    def styles: List[String]
  }

  /**
    * @param catalogUri  URI of the GeoTrellis catalog
    * @param name name of the layer, correct zoom level will be auto selected
    * @param zoom zoom of base layer
    * @param bandCount number of bands contained for each tile in the layer
    * @param styles Style names available for this layer
    */
  case class GeoTrellisLayer(
    catalogUri: String,
    name: String, zoom: Int,
    bandCount: Int = 1,
    styles: List[String]
  ) extends Layer

  case class GeoTiffLayer(
    geoTiffUri: String,
    name: String,
    styles: List[String]
  ) extends Layer

  lazy val conf: Conf = pureconfig.loadConfigOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf
}
