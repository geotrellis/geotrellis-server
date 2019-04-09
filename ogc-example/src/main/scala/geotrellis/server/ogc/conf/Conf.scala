package geotrellis.server.ogc.conf

import geotrellis.server.ogc.ows
import geotrellis.server.ogc.wms.WmsParentLayerMeta

import pureconfig.ConfigReader
import scalaxb.DataRecord

/**
 * The top level configuration object for all layers and styles.
 * This object should be supplied by the various sections in the provided configuration. If
 * the application won't start because of a bad configuration, start here and recursively
 * descend through properties verifying that the configuration file provides sufficient
 * information.
 *
 * Complex types can be read with the help of [[ConfigReader]] instances. See package.scala
 *  and https://pureconfig.github.io/docs/supporting-new-types.html for more examples and
 *  explanation of ConfigReader instances.
 */
case class Conf(
  layers: Map[String, OgcSourceConf],
  wms: WmsConf,
  wmts: WmtsConf,
  wcs: WcsConf
)

object Conf {
  lazy val conf: Conf = pureconfig.loadConfigOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf

  // This is a work-around to use pureconfig to read scalaxb generated case classes
  // DataRecord should never be specified from configuration, this satisfied the resolution
  // ConfigReader should be the containing class if DataRecord values need to be set
  implicit def dataRecordReader: ConfigReader[DataRecord[Any]] = null
}
