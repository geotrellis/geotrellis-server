package geotrellis.server.ogc.conf

import java.net.{InetAddress, URL}

import geotrellis.server.ogc.wms.wmsScope
import pureconfig.ConfigReader
import scalaxb.DataRecord

case class WMS(serviceMetadata: opengis.wms.Service)

case class Conf(
  http: Conf.Http,
  service: Conf.Service,
  wms: WMS,
  layers: List[OgcSourceConf]
) {
    def serviceUrlWms: URL = {
      // TODO: move decision to attach WMS to the point where we decide which service (wms, wmts, wcs) to bind
      service.url.getOrElse(
        if (http.interface == "0.0.0.0")
          new URL("http", InetAddress.getLocalHost.getHostAddress, http.port, "/wmts")
        else
          new URL("http", http.interface, http.port, "/wmts")
      )
    }
    def serviceUrlWcs: URL = {
      // TODO: move decision to attach WMS to the point where we decide which service (wms, wmts, wcs) to bind
      service.url.getOrElse(
        if (http.interface == "0.0.0.0")
          new URL("http", InetAddress.getLocalHost.getHostAddress, http.port, "/wcs")
        else
          new URL("http", http.interface, http.port, "/wcs")
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

  implicit def nameConfigReader: ConfigReader[opengis.wms.Name] =
    ConfigReader[String].map { str =>
      opengis.wms.Name.fromString(str, wmsScope)
    }

  implicit def keywordConfigReader: ConfigReader[opengis.wms.Keyword] =
    ConfigReader[String].map { str =>
      opengis.wms.Keyword(str)
    }

  // This is a work-around to use pureconfig to read scalaxb generated case classes
  // DataRecord should never be specified from configuration, this satisfied the resolution
  // ConfigReader should be the containing class if DataRecord values need to be set
  implicit def dataRecordReader: ConfigReader[DataRecord[Any]] = null
}
