package geotrellis.server.ogc.conf

import geotrellis.server.ogc.ows
import geotrellis.server.ogc.{OgcSource, SimpleSource}

import java.net.{InetAddress, URL}

import pureconfig.ConfigReader
import scalaxb.DataRecord


case class Conf(
  http: Conf.Http,
  service: Conf.Service,
  layers: Map[String, OgcSourceConf],
  wms: Conf.WMS,
  wmts: Conf.WMTS,
  wcs: Conf.WCS
) {
  def serviceUrl(path: String): URL = {
    // TODO: move decision to attach WMS to the point where we decide which service (wms, wmts, wcs) to bind
    service.url.getOrElse(
      if (http.interface == "0.0.0.0")
        new URL("http", InetAddress.getLocalHost.getHostAddress, http.port, path)
      else
        new URL("http", http.interface, http.port, path)
    )
  }
}

object Conf {
  trait OgcService {
    def layerDefinitions: List[OgcSourceConf]
    def layerSources(simpleSources: List[SimpleSource]): List[OgcSource] = {
      val simpleLayers =
        layerDefinitions.collect { case ssc@SimpleSourceConf(_, _, _, _) => ssc.model }
      val mapAlgebraLayers =
        layerDefinitions.collect { case masc@MapAlgebraSourceConf(_, _, _, _) => masc.model(simpleSources) }
      simpleLayers ++ mapAlgebraLayers
    }
  }
  case class WMS(serviceMetadata: opengis.wms.Service, layerDefinitions: List[OgcSourceConf]) extends OgcService
  case class WMTS(serviceMetadata: ows.ServiceMetadata, layerDefinitions: List[OgcSourceConf]) extends OgcService
  case class WCS(serviceMetadata: ows.ServiceMetadata, layerDefinitions: List[OgcSourceConf]) extends OgcService

  /** Public URL for this service that will be reported.
    * This may need to be set externall due to containerization or proxies.
    */
  case class Service(url: Option[URL])

  /** Local interface and port binding for the service */
  case class Http(interface: String, port: Int)

  lazy val conf: Conf = pureconfig.loadConfigOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf

  // This is a work-around to use pureconfig to read scalaxb generated case classes
  // DataRecord should never be specified from configuration, this satisfied the resolution
  // ConfigReader should be the containing class if DataRecord values need to be set
  implicit def dataRecordReader: ConfigReader[DataRecord[Any]] = null
}
