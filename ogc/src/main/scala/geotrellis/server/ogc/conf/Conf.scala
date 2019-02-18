package geotrellis.server.ogc.conf

import java.net.{InetAddress, URL}

case class Conf(
  http: Conf.Http,
  service: Conf.Service,
  layers: List[LayerSourceConf]
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

  lazy val conf: Conf = pureconfig.loadConfigOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf
}
