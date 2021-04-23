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

package geotrellis.raster.effects

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.proj4._
import geotrellis.raster.io.geotiff.OverviewStrategy

import cats._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.syntax.functor._
import cats.syntax.apply._
import cats.instances.list._

abstract class RasterSourceF[F[_]: Monad] extends RasterMetadataF[F] with Serializable {
  /** All available RasterSource metadata */
  def metadata: F[_ <: RasterMetadata]

  protected def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): RasterSourceF[F]

  /** Reproject to different CRS with explicit sampling reprojectOptions.
   * @see [[geotrellis.raster.reproject.Reproject]]
   * @group reproject
   */
  def reproject(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): RasterSourceF[F] =
    if (targetCRS == this.crs) this // TODO: Fixme, Embed would work here? how to short circuit
    else reprojection(targetCRS, resampleTarget, method, strategy)


  /** Sampling grid and resolution is defined by given [[GridExtent]].
   * Resulting extent is the extent of the minimum enclosing pixel region
   *   of the data footprint in the target grid.
   * @group reproject a
   */
  def reprojectToGrid(targetCRS: CRS, grid: GridExtent[Long], method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): RasterSourceF[F] =
    if (targetCRS == this.crs && grid == this.gridExtent) this // TODO: Fixme, Embed would work here? how to short circuit
    else if (targetCRS == this.crs) resampleToGrid(grid, method, strategy) // TODO: Fixme, Embed would work here? how to short circuit
    else reprojection(targetCRS, TargetAlignment(grid), method, strategy)

  /** Sampling grid and resolution is defined by given [[RasterExtent]] region.
   * The extent of the result is also taken from given [[RasterExtent]],
   *   this region may be larger or smaller than the footprint of the data
   * @group reproject
   */
  def reprojectToRegion(targetCRS: CRS, region: RasterExtent, method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): RasterSourceF[F] =
    if (targetCRS == this.crs && region == this.gridExtent) this
    else if (targetCRS == this.crs) resampleToRegion(region.toGridType[Long], method, strategy)
    else reprojection(targetCRS, TargetRegion(region.toGridType[Long]), method, strategy)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): RasterSourceF[F]

  /** Sampling grid is defined of the footprint of the data with resolution implied by column and row count.
   * @group resample
   */
  def resample(targetCols: Long, targetRows: Long, method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): RasterSourceF[F] =
    resample(TargetDimensions(targetCols, targetRows), method, strategy)

  /** Sampling grid and resolution is defined by given [[GridExtent]].
   * Resulting extent is the extent of the minimum enclosing pixel region
   *  of the data footprint in the target grid.
   * @group resample
   */
  def resampleToGrid(grid: GridExtent[Long], method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): RasterSourceF[F] =
    resample(TargetAlignment(grid), method, strategy)

  /** Sampling grid and resolution is defined by given [[RasterExtent]] region.
   * The extent of the result is also taken from given [[RasterExtent]],
   *   this region may be larger or smaller than the footprint of the data
   * @group resample
   */
  def resampleToRegion(region: GridExtent[Long], method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): RasterSourceF[F] =
    resample(TargetRegion(region), method, strategy)

  /** Reads a window for the extent.
   * Return extent may be smaller than requested extent around raster edges.
   * May return None if the requested extent does not overlap the raster extent.
   * @group read
   */
  @throws[IndexOutOfBoundsException]("if requested bands do not exist")
  def read(extent: Extent, bands: Seq[Int]): F[Raster[MultibandTile]]

  /** Reads a window for pixel bounds.
   * Return extent may be smaller than requested extent around raster edges.
   * May return None if the requested extent does not overlap the raster extent.
   * @group read
   */
  @throws[IndexOutOfBoundsException]("if requested bands do not exist")
  def read(bounds: GridBounds[Long], bands: Seq[Int]): F[Raster[MultibandTile]]

  /**
   * @group read
   */
  def read(extent: Extent): F[Raster[MultibandTile]] =
    bandCount >>= { bandCount => read(extent, 0 until bandCount) }

  /**
   * @group read
   */
  def read(bounds: GridBounds[Long]): F[Raster[MultibandTile]] =
    bandCount >>= { bandCount => read(bounds, 0 until bandCount) }

  /**
   * @group read
   */
  def read(): F[Raster[MultibandTile]] =
    (bandCount, extent).mapN { (bandCount, extent) => read(extent, 0 until bandCount) }.flatten

  /**
   * @group read
   */
  def read(bands: Seq[Int]): F[Raster[MultibandTile]] =
    extent >>= (read(_, bands))

  /**
   * @group read
   */
  def readExtents(extents: Traversable[Extent], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] =
    extents.toList.traverse(read(_, bands)).map(_.toIterator)

  /**
   * @group read
   */
  def readExtents(extents: Traversable[Extent]): F[Iterator[Raster[MultibandTile]]] =
    bandCount >>= { bandCount => readExtents(extents, 0 until bandCount) }
  /**
   * @group read
   */
  def readBounds(bounds: Traversable[GridBounds[Long]], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] =
    bounds.toList.traverse(read(_, bands)).map(_.toIterator)

  /**
   * @group read
   */
  def readBounds(bounds: Traversable[GridBounds[Long]]): F[Iterator[Raster[MultibandTile]]] =
    bounds
      .toList
      .traverse { bounds => bandCount >>= { bandCount => read(bounds, 0 until bandCount) } }
      .map(_.toIterator)

  private[raster] def targetCellType: Option[TargetCellType]

  protected[raster] lazy val dstCellType: Option[CellType] =
    targetCellType match {
      case Some(target) => Some(target.cellType)
      case None => None
    }

  protected lazy val convertRaster: Raster[MultibandTile] => Raster[MultibandTile] =
    targetCellType match {
      case Some(target: TargetCellType) =>
        (raster: Raster[MultibandTile]) => target(raster)
      case _ =>
        (raster: Raster[MultibandTile]) => raster
    }

  def convert(targetCellType: TargetCellType): RasterSourceF[F]

  /** Converts the values within the RasterSource from one [[CellType]] to another.
   *
   *  Note:
   *
   *  [[GDALRasterSource]] differs in how it converts data from the other RasterSources.
   *  Please see the convert docs for [[GDALRasterSource]] for more information.
   *  @group convert
   */
  def convert(targetCellType: CellType): RasterSourceF[F] =
    convert(ConvertTargetCellType(targetCellType))

  def interpretAs(targetCellType: CellType): RasterSourceF[F] =
    convert(InterpretAsTargetCellType(targetCellType))
}
