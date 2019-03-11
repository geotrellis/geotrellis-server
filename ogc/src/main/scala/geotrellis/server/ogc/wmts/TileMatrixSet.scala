package geotrellis.server.ogc.wmts

import geotrellis.proj4.{CRS, WebMercator, LatLng}
import geotrellis.raster.TileLayout
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.vector.Extent

import opengis.ows._
import opengis.wmts._
import opengis.wmts.{TileMatrix => TileMatrixXml, TileMatrixSet => TileMatrixSetXml}
import java.net.{InetAddress, URI}

case class TileMatrixSet(
  identifier: String,
  supportedCrs: CRS,
  title: Option[String] = None,
  `abstract`: Option[String] = None,
  boundingBox: Option[Extent] = None,
  wellKnownScaleSet: Option[String] = None,
  tileMatrix: List[TileMatrix]
) {
  def toXml = {
    val ret =  TileMatrixSetXml(
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

object TileMatrixSet {
  final val GoogleMapsCompatible: TileMatrixSet = {
    // EPSG:3857 world extent
    val extent = Extent(-20037508.34278925, -20037508.34278925, 20037508.34278925, 20037508.34278925)

    val tileMatrix: List[TileMatrix] = {
      for (zoom <- 1 to 21) yield {
        val tiles: Int = math.pow(2, zoom).toInt
        val tileLayout = TileLayout(layoutCols = tiles, layoutRows = tiles, tileCols = 256, tileRows = 256)
        TileMatrix(identifier = zoom.toString, extent = extent, tileLayout = tileLayout)
      }
    }.toList

    TileMatrixSet(
      identifier = "GoogleMapsCompatible",
      title = Some("GoogleMapsCompatible"),
      supportedCrs = WebMercator,
      wellKnownScaleSet = Some("urn:ogc:def:wkss:OGC:1.0:GoogleMapsCompatible"),
      tileMatrix = tileMatrix)
  }


  def forWellKnownScaleSet(urn: String): Option[TileMatrixSet] = {
    // TODO: turn this into SPI so this list can be user expandable
    urn match {
      case "urn:ogc:def:wkss:OGC:1.0:GoogleMapsCompatible" => Some(GoogleMapsCompatible)
      case _ => None
    }
  }
}