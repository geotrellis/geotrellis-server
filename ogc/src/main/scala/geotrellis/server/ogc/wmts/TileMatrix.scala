package geotrellis.server.ogc.wmts

import geotrellis.proj4.{CRS, WebMercator, LatLng}
import geotrellis.raster.TileLayout
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.vector.Extent

import opengis.ows._
import opengis.wmts._
import opengis.wmts.{TileMatrix => TileMatrixXml, TileMatrixSet => TileMatrixSetXml}
import java.net.{InetAddress, URI}

case class TileMatrix(
  title: String,
  _abstract: String,
  identifier: String,
  extent: Extent,
  tileLayout: TileLayout
) {
  val projectionMetersPerUnit = Map[CRS, Double](
    // meters per unit on equator
    LatLng -> 6378137.0 * 2.0 * math.Pi / 360.0,
    WebMercator -> 1
  )

  def toLayout(crs: CRS): LayoutDefinition = {
    val reprojectedExtent = extent.reproject(LatLng, crs)
    val layout = LayoutDefinition(reprojectedExtent, tileLayout)
    require(layout.cellSize.width == layout.cellSize.height,
      s"Layout definition cell size width must be same as height: ${layout.cellSize}")
    layout
  }

  def toXml(crs: CRS): TileMatrixXml = {
    val layoutDefinition = toLayout(crs)
    projectionMetersPerUnit.get(crs) match {
      case Some(metersPerUnit) =>
        val scaleDenominator = layoutDefinition.cellSize.width / 0.00028 * metersPerUnit
        TileMatrixXml(
          Title = LanguageStringType(title) :: Nil,
          Abstract = LanguageStringType(_abstract) :: Nil,
          Keywords = Nil,
          Identifier = CodeType(identifier),
          ScaleDenominator = scaleDenominator,
          TopLeftCorner = List(layoutDefinition.extent.xmin, layoutDefinition.extent.ymax),
          TileWidth = layoutDefinition.tileLayout.tileCols,
          TileHeight = layoutDefinition.tileLayout.tileRows,
          MatrixWidth = layoutDefinition.tileLayout.layoutCols,
          MatrixHeight = layoutDefinition.tileLayout.layoutRows
        )
      case None =>
        throw new Exception(s"Invalid CRS: ${crs}")
    }
  }
}
