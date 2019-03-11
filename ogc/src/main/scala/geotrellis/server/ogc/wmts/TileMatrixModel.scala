package geotrellis.server.ogc.wmts

import geotrellis.contrib.vlm.geotiff._
import geotrellis.contrib.vlm.gdal._
import geotrellis.contrib.vlm.avro._
import geotrellis.spark.tiling._
import geotrellis.spark._
import geotrellis.proj4._
import geotrellis.raster._
import java.io.File
import java.net._

case class TileMatrixModel(
  matrices: List[TileMatrixSet]
) {

  val matrixSetLookup: Map[String, TileMatrixSet] =
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
