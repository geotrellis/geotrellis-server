package geotrellis.server.ogc

import scala.xml.NamespaceBinding

package object wms {
  val wmsScope: NamespaceBinding = scalaxb.toScope(
    Some("ogc") -> "http://www.opengis.net/ogc",
    Some("wms") -> "http://www.opengis.net/wms",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )

  /**
    * Default scope generates an incorrect XML file (in the incorrect scope, prefixes all XML elements with `wms:` prefix.
    *
    * val defaultScope = scalaxb.toScope(Some("ogc") -> "http://www.opengis.net/ogc",
    * Some("wms") -> "http://www.opengis.net/wms",
    * Some("xlink") -> "http://www.w3.org/1999/xlink",
    * Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    * Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance")
    */
  val constrainedWMSScope: NamespaceBinding = scalaxb.toScope(
    Some("ogc") -> "http://www.opengis.net/ogc",
    Some("xlink") -> "http://www.w3.org/1999/xlink",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )
}
