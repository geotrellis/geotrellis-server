package geotrellis.server.ogc.wcs.ops

import geotrellis.server.ogc.wcs._
import geotrellis.server.ogc.wcs.params.GetCapabilitiesWcsParams

import com.typesafe.scalalogging.LazyLogging

import scala.xml._

trait GetCapabilities extends LazyLogging {
  // Cribbed from https://github.com/ngageoint/mrgeo/blob/master/mrgeo-services/mrgeo-services-wcs/src/main/java/org/mrgeo/services/wcs/WcsCapabilities.java
  def build(requestURL: String, wcsModel: WcsModel, params: GetCapabilitiesWcsParams): Elem
}
