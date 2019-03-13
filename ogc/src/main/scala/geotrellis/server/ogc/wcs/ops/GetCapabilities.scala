package geotrellis.server.ogc.wcs.ops

import geotrellis.server.ogc._
import geotrellis.server.ogc.wcs.params.GetCapabilitiesWcsParams

import geotrellis.spark._
import geotrellis.spark.io._
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try
import scala.xml._

trait GetCapabilities extends LazyLogging {
  // Cribbed from https://github.com/ngageoint/mrgeo/blob/master/mrgeo-services/mrgeo-services-wcs/src/main/java/org/mrgeo/services/wcs/WcsCapabilities.java
  def build(metadata: ows.ServiceMetadata, requestURL: String, rsm: RasterSourcesModel, params: GetCapabilitiesWcsParams): Elem
}
