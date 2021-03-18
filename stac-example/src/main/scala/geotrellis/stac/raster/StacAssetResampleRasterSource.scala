package geotrellis.stac.raster

import geotrellis.stac._

import geotrellis.proj4.CRS
import geotrellis.raster._
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster.resample.ResampleMethod
import geotrellis.stac.StacItemProperties
import geotrellis.vector.Extent
import geotrellis.raster.reproject.{Reproject, ReprojectRasterExtent}
import com.azavea.stac4s.StacItemAsset
import cats.syntax.option._

class StacAssetResampleRasterSource(
  val asset: StacItemAsset,
  val itemProperties: StacItemProperties,
  val resampleTarget: ResampleTarget,
  val resampleMethod: ResampleMethod,
  val strategy: OverviewStrategy,
  private[geotrellis] val targetCellType: Option[TargetCellType],
  @transient underlyingRS: => Option[RasterSource]
) extends RasterSource {
  @transient private lazy val underlying = underlyingRS.getOrElse(RasterSource(asset.href))
  @transient private lazy val underlyingResampled = underlying.resample(resampleTarget, resampleMethod, strategy)

  lazy val gridExtent: GridExtent[Long] = resampleTarget(asset.gridExtent.orElse(itemProperties.gridExtent).getOrElse(underlying.gridExtent))

  def metadata: StacAssetMetadata                       = StacAssetMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, itemProperties)
  val name: SourceName                                  = asset.href
  def crs: CRS                                          = asset.crs.orElse(itemProperties.crs).getOrElse(underlying.crs)
  def bandCount: Int                                    = itemProperties.bandCount.getOrElse(underlyingResampled.bandCount)
  def cellType: CellType                                = underlyingResampled.cellType
  def resolutions: List[CellSize]                       = underlyingResampled.resolutions
  def attributes: Map[String, String]                   = itemProperties.toMap.mapValues(_.as[String].toOption).collect { case (k, v) if v.nonEmpty => k -> v.get }
  def attributesForBand(band: Int): Map[String, String] = Map.empty

  def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetReprojectRasterSource =
    new StacAssetReprojectRasterSource(asset, itemProperties, targetCRS, resampleTarget, method, strategy, underlyingRS = underlyingRS, targetCellType = targetCellType) {
      override lazy val gridExtent: GridExtent[Long] = {
        val reprojectedRasterExtent =
          ReprojectRasterExtent(
            baseGridExtent,
            transform,
            Reproject.Options.DEFAULT.copy(method = resampleMethod, errorThreshold = errorThreshold)
          )

        resampleTarget(reprojectedRasterExtent)
      }
    }

  def resample(resampleTarget: ResampleTarget, resampleMethod: ResampleMethod, strategy: OverviewStrategy): StacAssetResampleRasterSource =
    StacAssetResampleRasterSource(asset, itemProperties, resampleTarget, resampleMethod, strategy, targetCellType, underlyingRS)

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlyingResampled.read(extent, bands)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlyingResampled.read(bounds, bands)

  def convert(targetCellType: TargetCellType): StacAssetResampleRasterSource =
    StacAssetResampleRasterSource(asset, itemProperties, resampleTarget, resampleMethod, strategy, underlyingRS = underlyingResampled.convert(targetCellType).some, targetCellType = targetCellType.some)
}

object StacAssetResampleRasterSource {
  def apply(
    asset: StacItemAsset,
    itemProperties: StacItemProperties = StacItemProperties.EMPTY,
    resampleTarget: ResampleTarget,
    resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
    targetCellType: Option[TargetCellType] = None,
    underlyingRS: => Option[RasterSource] = None
  ): StacAssetResampleRasterSource =
    new StacAssetResampleRasterSource(asset, itemProperties, resampleTarget, resampleMethod, strategy, targetCellType, underlyingRS)
}
