package geotrellis.server.ogc.conf

import geotrellis.server.ogc.ows
import geotrellis.server.ogc.wms.WmsParentLayerMeta
import geotrellis.server.ogc.{OgcSource, SimpleSource}
import geotrellis.server.ogc.wmts.GeotrellisTileMatrixSet

import java.net.{InetAddress, URL}

import pureconfig.ConfigReader
import scalaxb.DataRecord

case class Conf(
  layers: Map[String, OgcSourceConf],
  wms: Conf.WMS,
  wmts: Conf.WMTS,
  wcs: Conf.WCS
)

object Conf {
  trait OgcServiceConf {
    def layerDefinitions: List[OgcSourceConf]
    def layerSources(simpleSources: List[SimpleSource]): List[OgcSource] = {
      val simpleLayers =
        layerDefinitions.collect { case ssc@SimpleSourceConf(_, _, _, _) => ssc.model }
      val mapAlgebraLayers =
        layerDefinitions.collect { case masc@MapAlgebraSourceConf(_, _, _, _) => masc.model(simpleSources) }
      simpleLayers ++ mapAlgebraLayers
    }
  }
  case class WMS(
    parentLayerMeta: WmsParentLayerMeta,
    serviceMetadata: opengis.wms.Service,
    layerDefinitions: List[OgcSourceConf]
  ) extends OgcServiceConf

  case class WMTS(
    serviceMetadata: ows.ServiceMetadata,
    layerDefinitions: List[OgcSourceConf],
    tileMatrixSets: List[GeotrellisTileMatrixSet]
  ) extends OgcServiceConf

  case class WCS(
    serviceMetadata: ows.ServiceMetadata,
    layerDefinitions: List[OgcSourceConf]
  ) extends OgcServiceConf

  lazy val conf: Conf = pureconfig.loadConfigOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf

  // This is a work-around to use pureconfig to read scalaxb generated case classes
  // DataRecord should never be specified from configuration, this satisfied the resolution
  // ConfigReader should be the containing class if DataRecord values need to be set
  implicit def dataRecordReader: ConfigReader[DataRecord[Any]] = null
}
