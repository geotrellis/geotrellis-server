package geotrellis.stac.raster

import geotrellis.stac._

import geotrellis.proj4.{CRS, Transform}
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster._
import geotrellis.vector.Extent
import geotrellis.raster.reproject.{Reproject, ReprojectRasterExtent}
import geotrellis.raster.resample.ResampleMethod
import com.azavea.stac4s.StacItemAsset
import cats.syntax.option._

class StacAssetReprojectRasterSource(
  val asset: StacItemAsset,
  val itemProperties: StacItemProperties = StacItemProperties.EMPTY,
  // should be lazy
  val crs: CRS,
  val resampleTarget: ResampleTarget = DefaultTarget,
  val resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
  val strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
  val errorThreshold: Double = 0.125,
  private[geotrellis] val targetCellType: Option[TargetCellType] = None,
  @transient underlyingRS: => Option[RasterSource] = None
) extends RasterSource {
  @transient private lazy val underlying = underlyingRS.getOrElse(RasterSource(asset.href))
  @transient private lazy val underlyingReprojected = underlying.reproject(crs, resampleTarget, resampleMethod, strategy)

  protected lazy val baseCRS: CRS = asset.crs.orElse(itemProperties.crs).getOrElse(underlying.crs)
  protected lazy val baseGridExtent: GridExtent[Long] = asset.gridExtent.orElse(itemProperties.gridExtent).getOrElse(underlying.gridExtent)

  // TODO: remove transient notation with Proj4 1.1 release
  @transient protected lazy val transform = Transform(baseCRS, crs)

  lazy val gridExtent: GridExtent[Long] = {
    lazy val reprojectedRasterExtent =
      ReprojectRasterExtent(
        baseGridExtent,
        transform,
        Reproject.Options.DEFAULT.copy(method = resampleMethod, errorThreshold = errorThreshold)
      )

    resampleTarget(reprojectedRasterExtent)
  }

  def metadata: StacAssetMetadata                       = StacAssetMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, itemProperties)
  val name: SourceName                                  = asset.href
  def bandCount: Int                                    = itemProperties.bandCount.getOrElse(underlyingReprojected.bandCount)
  def cellType: CellType                                = underlyingReprojected.cellType
  def resolutions: List[CellSize]                       = underlyingReprojected.resolutions
  def attributes: Map[String, String]                   = itemProperties.toMap.mapValues(_.as[String].toOption).collect { case (k, v) if v.nonEmpty => k -> v.get }
  def attributesForBand(band: Int): Map[String, String] = Map.empty

  def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(asset, itemProperties, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType, underlyingRS = underlyingRS)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(asset, itemProperties, crs, resampleTarget, method, strategy, targetCellType = targetCellType, underlyingRS = underlyingRS)

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlyingReprojected.read(extent, bands)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlyingReprojected.read(bounds, bands)

  def convert(targetCellType: TargetCellType): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(asset, itemProperties, crs, resampleTarget, resampleMethod, strategy, targetCellType = targetCellType.some, underlyingRS = underlyingReprojected.convert(targetCellType).some)
}

object StacAssetReprojectRasterSource {
  def apply(
    asset: StacItemAsset,
    itemProperties: StacItemProperties = StacItemProperties.EMPTY,
    crs: CRS,
    resampleTarget: ResampleTarget = DefaultTarget,
    resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
    errorThreshold: Double = 0.125,
    targetCellType: Option[TargetCellType] = None,
    underlyingRS: => Option[RasterSource] = None
  ): StacAssetReprojectRasterSource =
    new StacAssetReprojectRasterSource(asset, itemProperties, crs, resampleTarget, resampleMethod, strategy, errorThreshold, targetCellType, underlyingRS)
}
