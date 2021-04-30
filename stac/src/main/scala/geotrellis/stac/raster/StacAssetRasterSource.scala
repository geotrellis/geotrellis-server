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

import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster._
import geotrellis.vector.Extent
import cats.syntax.option._
import cats.syntax.flatMap._
import cats.instances.option._

class StacAssetRasterSource(
  val asset: StacItemAsset,
  private[geotrellis] val targetCellType: Option[TargetCellType],
  @transient underlyingRS: => Option[RasterSource]
) extends RasterSource {
  @transient private lazy val underlying = underlyingRS.getOrElse(RasterSource(asset.href))

  val name: SourceName                                  = asset.href
  def crs: CRS                                          = asset.crs.getOrElse(underlying.crs)
  def bandCount: Int                                    = asset.bandCount.getOrElse(underlying.bandCount)
  def cellType: CellType                                = underlying.cellType
  def gridExtent: GridExtent[Long]                      = asset.gridExtent.getOrElse(underlying.gridExtent)
  def resolutions: List[CellSize]                       = underlying.resolutions
  def attributes: Map[String, String]                   =
    asset.item.properties.toMap.mapValues(_.as[String].toOption).collect { case (k, v) if v.nonEmpty => k -> v.get }
  def attributesForBand(band: Int): Map[String, String] = Map.empty
  def metadata: StacItemAssetMetadata                   = StacItemAssetMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, asset)

  def reprojection(
    targetCRS: CRS,
    resampleTarget: ResampleTarget,
    method: ResampleMethod,
    strategy: OverviewStrategy
  ): StacAssetReprojectRasterSource =
    StacAssetReprojectRasterSource(asset, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType, underlyingRS = underlyingRS)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): StacAssetResampleRasterSource =
    StacAssetResampleRasterSource(asset, resampleTarget, method, strategy, targetCellType, underlyingRS)

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    extent.intersection(this.extent) >> underlying.read(extent, bands)

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    bounds.intersection(dimensions) >> underlying.read(bounds, bands)

  def convert(targetCellType: TargetCellType): StacAssetRasterSource =
    StacAssetRasterSource(asset, targetCellType.some, underlying.convert(targetCellType).some)

  override def toString: String = s"StacAssetRasterSource($asset, $targetCellType)"
}

object StacAssetRasterSource {
  def apply(
    asset: StacItemAsset,
    targetCellType: Option[TargetCellType] = None,
    underlyingRS: => Option[RasterSource] = None
  ): StacAssetRasterSource = new StacAssetRasterSource(asset, targetCellType, underlyingRS)
}
