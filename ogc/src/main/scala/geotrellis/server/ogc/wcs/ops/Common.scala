package geotrellis.server.ogc.wcs.ops

import geotrellis.server.ogc.URN

import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import scala.xml.{Attribute, NodeSeq, Null, Text}

object Common {
  def boundingBox110(ex: Extent, crs: CRS, tagName: String = "ows:BoundingBox") = {
    val elem = <temp dimensions="2"></temp>
    val childs = Seq(<ows:LowerCorner>{ex.xmin} {ex.ymin}</ows:LowerCorner>,
                     <ows:UpperCorner>{ex.xmax} {ex.ymax}</ows:UpperCorner>)
    if (tagName != "ows:WGS84BoundingBox")
      elem.copy(label=tagName, child=childs) % Attribute(None, "crs", Text(URN.unsafeFromCrs(crs)), Null)
    else
      elem.copy(label=tagName, child=childs)
  }
}
