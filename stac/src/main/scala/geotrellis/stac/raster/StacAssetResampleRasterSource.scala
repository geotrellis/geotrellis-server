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

import geotrellis.stac._
import geotrellis.proj4.CRS
import geotrellis.raster._
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster.resample.ResampleMethod
import geotrellis.vector.Extent
import geotrellis.raster.reproject.{Reproject, ReprojectRasterExtent}

import cats.syntax.option._
import cats.syntax.flatMap._
import cats.instances.option._

class StacAssetResampleRasterSource(
  val asset: StacItemAsset,
  val resampleTarget: ResampleTarget,
  val resampleMethod: ResampleMethod,
  val strategy: OverviewStrategy,
  private[geotrellis] val targetCellType: Option[TargetCellType],
  @transient underlyingRS: => Option[RasterSource]
) extends RasterSource {
  @transient private lazy val underlying          = underlyingRS.getOrElse(RasterSource(asset.href))
  @transient private lazy val underlyingResampled = underlying.resample(resampleTarget, resampleMethod, strategy)

  lazy val gridExtent: GridExtent[Long] = resampleTarget(asset.gridExtent.getOrElse(underlying.gridExtent))

  def metadata: StacItemAssetMetadata                   = StacItemAssetMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, asset)
  val name: SourceName                                  = asset.href
  def crs: CRS                                          = asset.crs.getOrElse(underlying.crs)
  def bandCount: Int                                    = asset.bandCount.getOrElse(underlyingResampled.bandCount)
  def cellType: CellType                                = underlyingResampled.cellType
  def resolutions: List[CellSize]                       = underlyingResampled.resolutions
  def attributes: Map[String, String]                   = asset.item.properties.toMap
  def attributesForBand(band: Int): Map[String, String] = Map.empty

  def reprojection(
    targetCRS: CRS,
    resampleTarget: ResampleTarget,
    method: ResampleMethod,
    strategy: OverviewStrategy
  ): StacAssetReprojectRasterSource =
    new StacAssetReprojectRasterSource(
      asset,
      targetCRS,
      resampleTarget,
      method,
      strategy,
      underlyingRS = underlyingRS,
      targetCellType = targetCellType
    ) {
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
    StacAssetResampleRasterSource(asset, resampleTarget, resampleMethod, strategy, targetCellType, underlyingRS)

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    extent.intersection(this.extent) >> underlyingResampled.read(extent, bands)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    bounds.intersection(dimensions) >> underlyingResampled.read(bounds, bands)

  def convert(targetCellType: TargetCellType): StacAssetResampleRasterSource =
    StacAssetResampleRasterSource(
      asset,
      resampleTarget,
      resampleMethod,
      strategy,
      underlyingRS = underlyingResampled.convert(targetCellType).some,
      targetCellType = targetCellType.some
    )

  override def toString: String = s"StacAssetResampleRasterSource($asset, $asset, $resampleTarget, $resampleMethod, $strategy, $targetCellType)"
}

object StacAssetResampleRasterSource {
  def apply(
    asset: StacItemAsset,
    resampleTarget: ResampleTarget,
    resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
    targetCellType: Option[TargetCellType] = None,
    underlyingRS: => Option[RasterSource] = None
  ): StacAssetResampleRasterSource =
    new StacAssetResampleRasterSource(asset, resampleTarget, resampleMethod, strategy, targetCellType, underlyingRS)
}
