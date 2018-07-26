package geotrellis.server.core.wcs.ops

import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import scala.xml.{Attribute, NodeSeq, Null, Text}

object Common {
  def crsToUrnString(crs: CRS) =
    "urn:ogc:def:crs:EPSG::" + crs.epsgCode.map(_.toString).getOrElse("")

  def boundingBox110(ex: Extent, crs: CRS, tagName: String = "ows:BoundingBox") = {
    val elem = <temp dimensions="2"></temp>
    val childs = Seq(<ows:LowerCorner>{ex.xmin} {ex.ymin}</ows:LowerCorner>,
                     <ows:UpperCorner>{ex.xmax} {ex.ymax}</ows:UpperCorner>)
    if (tagName != "ows:WGS84BoundingBox")
      elem.copy(label=tagName, child=childs) % Attribute(None, "crs", Text(crsToUrnString(crs)), Null)
    else
      elem.copy(label=tagName, child=childs)
  }
}
