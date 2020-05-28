package geotrellis.stac.raster

import geotrellis.proj4.CRS
import geotrellis.raster.{CellSize, CellType, GridExtent, RasterMetadata, SourceName}
import io.circe.JsonObject

case class StacItemAssetMetadata(
  name: SourceName,
  crs: CRS,
  bandCount: Int,
  cellType: CellType,
  gridExtent: GridExtent[Long],
  resolutions: List[CellSize],
  itemProperties: JsonObject = JsonObject.empty
) extends RasterMetadata {
  def attributes: Map[String, String] = itemProperties.toMap.mapValues(_.spaces4)
  def attributesForBand(band: Int): Map[String, String] = Map.empty
}
