package geotrellis.server.ogc

import geotrellis.vector.Extent
import scala.xml.NamespaceBinding

package object wcs {
  val wcsScope: NamespaceBinding = scalaxb.toScope(
    Some("gml") -> "http://www.opengis.net/gml",
    Some("ows") -> "http://www.opengis.net/ows/1.1",
    Some("ogc") -> "http://www.opengis.net/ogc",
    Some("wcs") -> "http://www.opengis.net/wcs/1.1.1",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  val constrainedWCSScope: NamespaceBinding = scalaxb.toScope(
    Some("gml") -> "http://www.opengis.net/gml",
    Some("ows") -> "http://www.opengis.net/ows/1.1",
    Some("ogc") -> "http://www.opengis.net/ogc",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  implicit class ExtentOps(self: Extent) {
    def swapXY: Extent = Extent(
      xmin = self.ymin,
      ymin = self.xmin,
      xmax = self.ymax,
      ymax = self.xmax
    )
  }
}
