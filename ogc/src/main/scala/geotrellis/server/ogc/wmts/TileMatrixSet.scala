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
  supportedCrs: CRS,
  title: String,
  _abstract: String,
  identifier: String,
  matrices: List[TileMatrix]
) {
  def toXml =
    TileMatrixSetXml(
      Title = LanguageStringType(title) :: Nil,
      Abstract = LanguageStringType(_abstract) :: Nil,
      Keywords = Nil,
      Identifier = CodeType(identifier),
      // TODO: bounding box for global layer
      BoundingBox = None,
      SupportedCRS = new URI(s"urn:ogc:def:crs:EPSG:9.2:${supportedCrs.epsgCode.getOrElse(4326)}"),
      WellKnownScaleSet = None,
      TileMatrix = matrices.map(_.toXml(supportedCrs))
    )
}
