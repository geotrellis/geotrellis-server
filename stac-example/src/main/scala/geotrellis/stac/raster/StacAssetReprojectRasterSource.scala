/*
 * Copyright 2021 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.stac.raster

import geotrellis.proj4.{CRS, Transform}
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster._
import geotrellis.vector.Extent
import geotrellis.raster.reproject.{Reproject, ReprojectRasterExtent}
import geotrellis.raster.resample.ResampleMethod
import cats.syntax.option._

class StacAssetReprojectRasterSource(
  val asset: StacAsset,
  val crs: CRS,
  val resampleTarget: ResampleTarget = DefaultTarget,
  val resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
  val strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
  val errorThreshold: Double = 0.125,
  private[geotrellis] val targetCellType: Option[TargetCellType] = None,
  @transient underlyingRS: => Option[RasterSource] = None
) extends RasterSource {
  @transient private lazy val underlying            = underlyingRS.getOrElse(RasterSource(asset.href))
  @transient private lazy val underlyingReprojected = underlying.reproject(crs, resampleTarget, resampleMethod, strategy)

  protected lazy val baseCRS: CRS                     = asset.crs.getOrElse(underlying.crs)
  protected lazy val baseGridExtent: GridExtent[Long] = asset.gridExtent.getOrElse(underlying.gridExtent)

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

  def metadata: StacAssetMetadata                       = StacAssetMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, asset)
  val name: SourceName                                  = asset.href
  def bandCount: Int                                    = asset.bandCount.getOrElse(underlyingReprojected.bandCount)
  def cellType: CellType                                = underlyingReprojected.cellType
  def resolutions: List[CellSize]                       = underlyingReprojected.resolutions
  def attributes: Map[String, String]                   =
    asset.item.properties.toMap.mapValues(_.as[String].toOption).collect { case (k, v) if v.nonEmpty => k -> v.get }
  def attributesForBand(band: Int): Map[String, String] = Map.empty

  def reprojection(
    targetCRS: CRS,
    resampleTarget: ResampleTarget,
    method: ResampleMethod,
    strategy: OverviewStrategy
  ): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(asset, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType, underlyingRS = underlyingRS)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(asset, crs, resampleTarget, method, strategy, targetCellType = targetCellType, underlyingRS = underlyingRS)

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlyingReprojected.read(extent, bands)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlyingReprojected.read(bounds, bands)

  def convert(targetCellType: TargetCellType): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(
      asset,
      crs,
      resampleTarget,
      resampleMethod,
      strategy,
      targetCellType = targetCellType.some,
      underlyingRS = underlyingReprojected.convert(targetCellType).some
    )

  override def toString: String =
    s"StacAssetReprojectRasterSource($asset, $crs, $resampleTarget, $resampleMethod, $strategy, $errorThreshold, $targetCellType)"
}

object StacAssetReprojectRasterSource {
  def apply(
    asset: StacAsset,
    crs: CRS,
    resampleTarget: ResampleTarget = DefaultTarget,
    resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
    errorThreshold: Double = 0.125,
    targetCellType: Option[TargetCellType] = None,
    underlyingRS: => Option[RasterSource] = None
  ): StacAssetReprojectRasterSource =
    new StacAssetReprojectRasterSource(asset, crs, resampleTarget, resampleMethod, strategy, errorThreshold, targetCellType, underlyingRS)
}
