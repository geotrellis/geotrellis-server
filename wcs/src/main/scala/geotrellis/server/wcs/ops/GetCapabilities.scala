package geotrellis.server.wcs.ops

import geotrellis.server.wcs.params.GetCapabilitiesWcsParams

import geotrellis.spark._
import geotrellis.spark.io._
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try
import scala.xml._

trait GetCapabilities extends LazyLogging {
  // Cribbed from https://github.com/ngageoint/mrgeo/blob/master/mrgeo-services/mrgeo-services-wcs/src/main/java/org/mrgeo/services/wcs/WcsCapabilities.java
  def build(requestURL: String, metadata: MetadataCatalog, params: GetCapabilitiesWcsParams): Elem
}
