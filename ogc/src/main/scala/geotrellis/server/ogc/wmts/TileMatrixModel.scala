package geotrellis.server.ogc.wmts

import geotrellis.spark.tiling._
import geotrellis.proj4._

case class TileMatrixModel(
  matrices: List[GeotrellisTileMatrixSet]
) {

  val matrixSetLookup: Map[String, GeotrellisTileMatrixSet] =
    matrices.map({tileMatrixSet => tileMatrixSet.identifier -> tileMatrixSet}).toMap

  def getLayoutDefinition(tileMatrixSetId: String, tileMatrixId: String): Option[LayoutDefinition] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
      matrix <- matrixSet.tileMatrix.find(_.identifier == tileMatrixId)
    } yield matrix.layout

  def getCrs(tileMatrixSetId: String): Option[CRS] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
    } yield matrixSet.supportedCrs
}
