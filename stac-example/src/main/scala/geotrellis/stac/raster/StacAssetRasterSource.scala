package geotrellis.stac.raster

import geotrellis.stac._

import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster._
import geotrellis.vector.Extent
import com.azavea.stac4s.StacItemAsset
import cats.syntax.option._

class StacAssetRasterSource(
  asset: StacItemAsset,
  itemProperties: StacItemProperties,
  private[geotrellis] val targetCellType: Option[TargetCellType],
  @transient underlyingRS: => Option[RasterSource]
) extends RasterSource {
  @transient private lazy val underlying = underlyingRS.getOrElse(RasterSource(asset.href))

  val name: SourceName                                  = asset.href
  def crs: CRS                                          = asset.crs.orElse(itemProperties.crs).getOrElse(underlying.crs)
  def bandCount: Int                                    = itemProperties.bandCount.getOrElse(underlying.bandCount)
  def cellType: CellType                                = underlying.cellType
  def gridExtent: GridExtent[Long]                      = asset.gridExtent.orElse(itemProperties.gridExtent).getOrElse(underlying.gridExtent)
  def resolutions: List[CellSize]                       = underlying.resolutions
  def attributes: Map[String, String]                   = itemProperties.toMap.mapValues(_.as[String].toOption).collect { case (k, v) if v.nonEmpty => k -> v.get }
  def attributesForBand(band: Int): Map[String, String] = Map.empty
  def metadata: StacAssetMetadata                       =  StacAssetMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, itemProperties)

  def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(asset, itemProperties, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType, underlyingRS = underlyingRS)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetResampleRasterSource =
    StacAssetResampleRasterSource(asset, itemProperties, resampleTarget, method, strategy, targetCellType, underlyingRS)

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlying.read(extent, bands)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlying.read(bounds, bands)

  def convert(targetCellType: TargetCellType): StacAssetRasterSource =
    StacAssetRasterSource(asset, itemProperties, targetCellType.some, underlying.convert(targetCellType).some)
}

object StacAssetRasterSource {
  def apply(
    asset: StacItemAsset,
    itemProperties: StacItemProperties = StacItemProperties.EMPTY,
    targetCellType: Option[TargetCellType] = None,
    underlyingRS: => Option[RasterSource] = None
  ): StacAssetRasterSource = new StacAssetRasterSource(asset, itemProperties, targetCellType, underlyingRS)
}
