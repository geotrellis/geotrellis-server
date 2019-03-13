package geotrellis.server.ogc.wmts

import geotrellis.proj4.{CRS, WebMercator}
import geotrellis.raster.TileLayout
import geotrellis.vector.Extent
import geotrellis.spark.tiling._

import opengis.ows._
import opengis._
import opengis.wmts.{TileMatrixSet => TileMatrixSetXml}

import java.net.URI

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
      BoundingBox = boundingBox.map(extent => scalaxb.DataRecord(Some("ows"), Some("ows:BoundingBox"), BoundingBoxType(
        LowerCorner = Seq(extent.xmin, extent.ymin),
        UpperCorner = Seq(extent.xmax, extent.ymax),
        attributes = Map("@crs" -> scalaxb.DataRecord(new URI(s"urn:ogc:def:crs:EPSG:9.2:${supportedCrs.epsgCode.get}")))
      ))),
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
    val extent = WebMercator.worldExtent

    val tileMatrix: List[TileMatrix] = {
      for (zoom <- 1 to 21) yield {
        val tiles: Int = math.pow(2, zoom).toInt
        val tileLayout = TileLayout(layoutCols = tiles, layoutRows = tiles, tileCols = 256, tileRows = 256)
        TileMatrix(identifier = zoom.toString, extent = extent, tileLayout = tileLayout)
      }
    }.toList

    TileMatrixSet(
      identifier = "GoogleMapsCompatible",
      boundingBox = Some(extent),
      title = Some("GoogleMapsCompatible"),
      supportedCrs = WebMercator,
      wellKnownScaleSet = Some("urn:ogc:def:wkss:OGC:1.0:GoogleMapsCompatible"),
      tileMatrix = tileMatrix
    )
  }


  def forWellKnownScaleSet(urn: String): Option[TileMatrixSet] = {
    // TODO: turn this into SPI so this list can be user expandable
    urn match {
      case "urn:ogc:def:wkss:OGC:1.0:GoogleMapsCompatible" => Some(GoogleMapsCompatible)
      case _ => None
    }
  }
}