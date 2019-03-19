package geotrellis.server.ogc.wmts

import geotrellis.server.ogc._
import geotrellis.spark.tiling._
import geotrellis.proj4._

case class WmtsModel(
  serviceMetadata: ows.ServiceMetadata,
  matrices: List[GeotrellisTileMatrixSet],
  sources: Seq[OgcSource]
) {

  val sourceLookup: Map[String, OgcSource] = sources.map({layer => layer.name -> layer}).toMap

  val matrixSetLookup: Map[String, GeotrellisTileMatrixSet] =
    matrices.map({tileMatrixSet => tileMatrixSet.identifier -> tileMatrixSet}).toMap

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
   *  to produce a [[TiledOgcLayer]]
   */
  def getLayer(crs: CRS, layerName: String, layout: LayoutDefinition, styleName: String): Option[TiledOgcLayer] = {
    for {
      source <- sourceLookup.get(layerName)
    } yield {
      val style: Option[StyleModel] = source.styles.find(_.name == styleName)
      source match {
        case MapAlgebraSource(name, title, rasterSources, algebra, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleTiledOgcLayer(name, title, crs, layout, rs, style)
          }
          MapAlgebraTiledOgcLayer(name, title, crs, layout, simpleLayers, algebra, style)
        case SimpleSource(name, title, rasterSource, styles) =>
          SimpleTiledOgcLayer(name, title, crs, layout, rasterSource, style)
      }
    }
  }

  def getMatrixLayoutDefinition(tileMatrixSetId: String, tileMatrixId: String): Option[LayoutDefinition] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
      matrix <- matrixSet.tileMatrix.find(_.identifier == tileMatrixId)
    } yield matrix.layout

  def getMatrixCrs(tileMatrixSetId: String): Option[CRS] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
    } yield matrixSet.supportedCrs
}
