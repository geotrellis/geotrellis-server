package geotrellis.stac.raster

import com.azavea.stac4s.StacItemAsset
import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster._
import geotrellis.vector.Extent
import _root_.io.circe.JsonObject
import cats.syntax.option._

case class StacAssetRasterSource(
  asset: StacItemAsset,
  itemProperties: JsonObject = JsonObject.empty,
  private[raster] val underlyingRS: Option[RasterSource] = None,
  private[geotrellis] val targetCellType: Option[TargetCellType] = None
) extends RasterSource {
  private lazy val underlying = underlyingRS.getOrElse(RasterSource(asset.href))

  def metadata: StacAssetMetadata = StacAssetMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, itemProperties)

  def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetRasterSource =
    StacAssetRasterSource(asset, itemProperties, underlying.reproject(targetCRS, resampleTarget, method, strategy).some, targetCellType)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetRasterSource =
    StacAssetRasterSource(asset, itemProperties, underlying.resample(resampleTarget, method, strategy).some, targetCellType)

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlying.read(extent, bands)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlying.read(bounds, bands)

  def convert(targetCellType: TargetCellType): StacAssetRasterSource =
    StacAssetRasterSource(asset, itemProperties, underlying.convert(targetCellType).some, targetCellType.some)

  val name: SourceName = asset.href
  def crs: CRS = underlying.crs
  def bandCount: Int = underlying.bandCount
  def cellType: CellType = underlying.cellType
  def gridExtent: GridExtent[Long] = underlying.gridExtent
  def resolutions: List[CellSize] = underlying.resolutions
  def attributes: Map[String, String] = itemProperties.toMap.mapValues(_.as[String].toOption).collect { case (k, v) if v.nonEmpty => k -> v.get }
  def attributesForBand(band: Int): Map[String, String] = Map.empty
}
