package geotrellis.server.ogc.wmts

import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import opengis.ows._
import opengis.wmts.TileMatrixSet

import java.net.URI

case class GeotrellisTileMatrixSet(
  identifier: String,
  supportedCrs: CRS,
  title: Option[String] = None,
  `abstract`: Option[String] = None,
  boundingBox: Option[Extent] = None,
  wellKnownScaleSet: Option[String] = None,
  tileMatrix: List[GeotrellisTileMatrix]
) {
  def toXml = {
    val ret =  TileMatrixSet(
      Title = title.map(LanguageStringType(_)).toList,
      Abstract = `abstract`.map(LanguageStringType(_)).toList,
      Keywords = Nil,
      Identifier = CodeType(identifier),
      TileMatrix = tileMatrix.map(_.toXml(supportedCrs)),
      SupportedCRS = new URI(s"urn:ogc:def:crs:EPSG:9.2:${supportedCrs.epsgCode.get}")
    )

    if (wellKnownScaleSet.isDefined) {
      ret.copy(WellKnownScaleSet = wellKnownScaleSet.map(new URI(_)))
    } else {
      ret.copy(BoundingBox = ???)
    }
  }
}
