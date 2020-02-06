package geotrellis.server.ogc.wmts

import geotrellis.server.ogc.URN
import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import opengis.ows._
import opengis.wmts.TileMatrixSet

import java.net.URI

/** A collection of tile matrices; most commonly forming a pyramid of different resolutions */
case class GeotrellisTileMatrixSet(
  identifier: String,
  supportedCrs: CRS,
  title: Option[String] = None,
  `abstract`: Option[String] = None,
  boundingBox: Option[Extent] = None,
  wellKnownScaleSet: Option[String] = None,
  tileMatrix: List[GeotrellisTileMatrix]
) {
  def toXml: TileMatrixSet = {
    val ret        =  TileMatrixSet(
      Title        = title.map(LanguageStringType(_)).toList,
      Abstract     = `abstract`.map(LanguageStringType(_)).toList,
      Keywords     = Nil,
      Identifier   = CodeType(identifier),
      TileMatrix   = tileMatrix.map(_.toXml(supportedCrs)),
      SupportedCRS = new URI(URN.unsafeFromCrs(supportedCrs))
    )

    if (wellKnownScaleSet.isDefined) {
      ret.copy(WellKnownScaleSet = wellKnownScaleSet.map(new URI(_)))
    } else {
      ret.copy(BoundingBox = ???)
    }
  }
}
