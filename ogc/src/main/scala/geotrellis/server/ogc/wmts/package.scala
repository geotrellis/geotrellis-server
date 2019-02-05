package geotrellis.server.ogc

import scala.xml.NamespaceBinding

package object wmts {
  val wmtsScope: NamespaceBinding = scalaxb.toScope(
    Some("gml") -> "http://www.opengis.net/gml",
    Some("ows") -> "http://www.opengis.net/ows/1.1",
    Some("wmts") -> "http://www.opengis.net/wmts/1.0",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  val constrainedWMTSScope: NamespaceBinding = scalaxb.toScope(
    Some("gml") -> "http://www.opengis.net/gml",
    Some("ows") -> "http://www.opengis.net/ows/1.1",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )
}
