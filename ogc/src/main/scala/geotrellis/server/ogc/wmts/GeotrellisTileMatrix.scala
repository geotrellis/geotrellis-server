package geotrellis.server.ogc.wmts

import geotrellis.proj4.{CRS, WebMercator, LatLng}
import geotrellis.raster.TileLayout
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.vector.Extent

import opengis.ows._
import opengis.wmts._
import opengis.wmts.{TileMatrix, TileMatrixSet}
import java.net.{InetAddress, URI}


case class GeotrellisTileMatrix(
  identifier: String,
  extent: Extent,
  tileLayout: TileLayout,
  title: Option[String] = None,
  `abstract`: Option[String] = None
) {
  val layout: LayoutDefinition = LayoutDefinition(extent, tileLayout)

  require(layout.cellSize.width == layout.cellSize.height,
    s"Layout definition cell size width must be same as height: ${layout.cellSize}")

  val projectionMetersPerUnit = Map[CRS, Double](
    // meters per unit on equator
    LatLng -> 6378137.0 * 2.0 * math.Pi / 360.0,
    WebMercator -> 1
  )

  def toXml(crs: CRS): TileMatrix = {
    projectionMetersPerUnit.get(crs) match {
      case Some(metersPerUnit) =>
        val scaleDenominator = layout.cellSize.width / 0.00028 * metersPerUnit
        TileMatrix(
          Title = title.map(LanguageStringType(_)).toList,
          Abstract = `abstract`.map(LanguageStringType(_)).toList,
          Keywords = Nil,
          Identifier = CodeType(identifier),
          ScaleDenominator = scaleDenominator,
          TopLeftCorner = List(layout.extent.xmin, layout.extent.ymax),
          TileWidth = layout.tileLayout.tileCols,
          TileHeight = layout.tileLayout.tileRows,
          MatrixWidth = layout.tileLayout.layoutCols,
          MatrixHeight = layout.tileLayout.layoutRows)

      case None =>
        throw new Exception(s"Invalid CRS: ${crs}")
    }
  }
}
