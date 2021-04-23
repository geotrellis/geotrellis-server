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

package geotrellis.raster.effects.geotiff

import geotrellis.raster.effects._
import geotrellis.raster.geotiff.{GeoTiffMetadata, GeoTiffPath}
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.raster.reproject.{Reproject, ReprojectRasterExtent}
import geotrellis.raster.resample.ResampleMethod
import geotrellis.vector.Extent
import geotrellis.util.RangeReader

import cats._
import cats.syntax.flatMap._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.option._

case class GeoTiffResampleRasterSource[F[_]: Monad: UnsafeLift](
  dataPath: GeoTiffPath,
  resampleTarget: ResampleTarget,
  method: ResampleMethod = ResampleMethod.DEFAULT,
  val strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
  private[raster] val targetCellType: Option[TargetCellType] = None,
  @transient private[raster] val baseTiff: Option[F[MultibandGeoTiff]] = None
) extends RasterSourceF[F] {
  def resampleMethod: Option[ResampleMethod] = Some(method)
  def name: GeoTiffPath = dataPath

  // memoize tiff, not useful only in a local fs case
  @transient lazy val tiff: MultibandGeoTiff = GeoTiffReader.readMultiband(RangeReader(dataPath.value), streaming = true)
  @transient lazy val tiffF: F[MultibandGeoTiff] = Option(baseTiff).flatten.getOrElse(UnsafeLift[F].apply(tiff))

  def bandCount: F[Int] = tiffF.map(_.bandCount)
  def cellType: F[CellType] = dstCellType.fold(tiffF.map(_.cellType))(_.pure[F])
  def tags: F[Tags] = tiffF.map(_.tags)
  def metadata: F[GeoTiffMetadata] = (name.pure[F], crs, bandCount, cellType, gridExtent, resolutions, tags).mapN(GeoTiffMetadata)

  /** Returns the GeoTiff head tags. */
  def attributes: F[Map[String, String]] = tags.map(_.headTags)
  /** Returns the GeoTiff per band tags. */
  def attributesForBand(band: Int): F[Map[String, String]] = tags.map(_.bandTags.lift(band).getOrElse(Map.empty))

  def crs: F[CRS] = tiffF.map(_.crs)

  lazy val gridExtent: F[GridExtent[Long]] = tiffF.map(tiff => resampleTarget(tiff.rasterExtent.toGridType[Long]))

  lazy val resolutions: F[List[CellSize]] = tiffF.map { tiff => tiff.cellSize :: tiff.overviews.map(_.cellSize) }

  @transient private[raster] lazy val closestTiffOverview: F[GeoTiff[MultibandTile]] =
    (tiffF, gridExtent).mapN { (tiff, gridExtent) => tiff.getClosestOverview(gridExtent.cellSize, strategy) }

  def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): GeoTiffReprojectRasterSource[F] =
    new GeoTiffReprojectRasterSource[F](dataPath, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType) {
      override lazy val gridExtent: F[GridExtent[Long]] = {
        (baseGridExtent, transform).mapN { (ge, t) =>
          resampleTarget(ReprojectRasterExtent(ge, t, Reproject.Options.DEFAULT.copy(method = resampleMethod, errorThreshold = errorThreshold)))
        }
      }
    }

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): RasterSourceF[F] =
    GeoTiffResampleRasterSource(dataPath, resampleTarget, method, strategy, targetCellType, tiffF.some)

  def convert(targetCellType: TargetCellType): RasterSourceF[F] =
    GeoTiffResampleRasterSource(dataPath, resampleTarget, method, strategy, Some(targetCellType), tiffF.some)

  def read(extent: Extent, bands: Seq[Int]): F[Raster[MultibandTile]] = {
    val bounds = gridExtent.map(_.gridBoundsFor(extent, clamp = false))
    bounds >>= (read(_, bands))
  }

  def read(bounds: GridBounds[Long], bands: Seq[Int]): F[Raster[MultibandTile]] =
    readBounds(List(bounds), bands) >>= { iter => closestTiffOverview.map { _.synchronized(iter.next) } }

  override def readExtents(extents: Traversable[Extent], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] = {
    val targetPixelBounds = gridExtent.map(gridExtent => extents.map(gridExtent.gridBoundsFor(_)))
    // result extents may actually expand to cover pixels at our resolution
    // TODO: verify the logic here, should the sourcePixelBounds be calculated from input or expanded extent?
    targetPixelBounds >>= (readBounds(_, bands))
  }

  override def readBounds(bounds: Traversable[GridBounds[Long]], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] =
    (closestTiffOverview, this.gridBounds, gridExtent, cellSize).tupled >>= { case (closestTiffOverview, gridBounds, gridExtent, cellSize) =>
      UnsafeLift[F].apply {
        val geoTiffTile = closestTiffOverview.tile.asInstanceOf[GeoTiffMultibandTile]

        val windows = {
          for {
            queryPixelBounds <- bounds
            targetPixelBounds <- queryPixelBounds.intersection(gridBounds)
          } yield {
            val targetExtent = gridExtent.extentFor(targetPixelBounds)
            // Buffer the targetExtent to read a buffered area from the source tiff
            // so the resample would behave properly on borders
            // Buffer by half of CS to avoid resampling out of bounds
            val bufferedTargetExtent = targetExtent.buffer(cellSize.width / 2, cellSize.height / 2)
            val sourcePixelBounds = closestTiffOverview.rasterExtent.gridBoundsFor(bufferedTargetExtent)
            val targetRasterExtent = RasterExtent(targetExtent, targetPixelBounds.width.toInt, targetPixelBounds.height.toInt)
            (sourcePixelBounds, targetRasterExtent)
          }
        }.toMap

        geoTiffTile.crop(windows.keys.toSeq, bands.toArray).map { case (gb, tile) =>
          val targetRasterExtent = windows(gb)
          val sourceExtent = closestTiffOverview.rasterExtent.extentFor(gb, clamp = false)
          Raster(tile, sourceExtent).resample(targetRasterExtent, method)
        }
      }
    }
}
