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
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.vector.Extent
import geotrellis.util.RangeReader

import cats._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.option._
import cats.instances.list._

case class GeoTiffReprojectRasterSource[F[_]: Monad: UnsafeLift](
    dataPath: GeoTiffPath,
    targetCRS: CRS,
    resampleTarget: ResampleTarget = DefaultTarget,
    resampleMethod: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT,
    errorThreshold: Double = 0.125,
    private[raster] val targetCellType: Option[TargetCellType] = None,
    @transient private[raster] val baseTiff: Option[F[MultibandGeoTiff]] = None
) extends RasterSourceF[F] {
  def name: GeoTiffPath = dataPath

  // memoize tiff, not useful only in a local fs case
  @transient lazy val tiff: MultibandGeoTiff     = GeoTiffReader.readMultiband(RangeReader(dataPath.value), streaming = true)
  @transient lazy val tiffF: F[MultibandGeoTiff] = Option(baseTiff).flatten.getOrElse(UnsafeLift[F].apply(tiff))

  def bandCount: F[Int]            = tiffF.map(_.bandCount)
  def cellType: F[CellType]        = dstCellType.fold(tiffF.map(_.cellType))(_.pure[F])
  def tags: F[Tags]                = tiffF.map(_.tags)
  def metadata: F[GeoTiffMetadata] = (name.pure[F], crs, bandCount, cellType, gridExtent, resolutions, tags).mapN(GeoTiffMetadata)

  /** Returns the GeoTiff head tags. */
  def attributes: F[Map[String, String]] = tags.map(_.headTags)

  /** Returns the GeoTiff per band tags. */
  def attributesForBand(band: Int): F[Map[String, String]] = tags.map(_.bandTags.lift(band).getOrElse(Map.empty))

  lazy val crs: F[CRS]                                   = Monad[F].pure(targetCRS)
  protected lazy val baseCRS: F[CRS]                     = tiffF.map(_.crs)
  protected lazy val baseGridExtent: F[GridExtent[Long]] = tiffF.map(_.rasterExtent.toGridType[Long])

  // TODO: remove transient notation with Proj4 1.1 release
  @transient protected lazy val transform     = (baseCRS, crs).mapN((baseCRS, crs) => Transform(baseCRS, crs))
  @transient protected lazy val backTransform = (crs, baseCRS).mapN((crs, baseCRS) => Transform(crs, baseCRS))

  override lazy val gridExtent: F[GridExtent[Long]] = {
    lazy val reprojectedRasterExtent: F[GridExtent[Long]] =
      (baseGridExtent, transform).mapN {
        ReprojectRasterExtent(_, _, Reproject.Options.DEFAULT.copy(method = resampleMethod, errorThreshold = errorThreshold))
      }

    reprojectedRasterExtent.map(resampleTarget(_))
  }

  lazy val resolutions: F[List[CellSize]] =
    (tiffF, transform).mapN { (tiff, transform) =>
      ReprojectRasterExtent(tiff.rasterExtent, transform, Reproject.Options.DEFAULT).cellSize ::
        tiff.overviews.map(ovr => ReprojectRasterExtent(ovr.rasterExtent, transform, Reproject.Options.DEFAULT).cellSize)
    }

  @transient private[raster] lazy val closestTiffOverview: F[GeoTiff[MultibandTile]] =
    resampleTarget match {
      case DefaultTarget =>
        (tiffF, baseGridExtent).mapN((tiff, ge) => tiff.getClosestOverview(ge.cellSize, strategy))
      case _ =>
        // we're asked to match specific target resolution, estimate what resolution we need in source to sample it
        (tiffF, backTransform, gridExtent).mapN { (tiff, backTransform, gridExtent) =>
          val estimatedSource = ReprojectRasterExtent(gridExtent, backTransform)
          tiff.getClosestOverview(estimatedSource.cellSize, strategy)
        }
    }

  def read(extent: Extent, bands: Seq[Int]): F[Raster[MultibandTile]] = {
    val bounds = gridExtent.map(_.gridBoundsFor(extent, clamp = false))
    bounds >>= (read(_, bands))
  }

  def read(bounds: GridBounds[Long], bands: Seq[Int]): F[Raster[MultibandTile]] =
    readBounds(List(bounds), bands) >>= { iter => closestTiffOverview.map(_.synchronized(iter.next)) }

  override def readExtents(extents: Traversable[Extent], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] = {
    val bounds: F[List[GridBounds[Long]]] = extents.toList.traverse(e => gridExtent.map(_.gridBoundsFor(e, clamp = true)))
    bounds >>= (readBounds(_, bands))
  }

  override def readBounds(bounds: Traversable[GridBounds[Long]], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] =
    (closestTiffOverview, gridBounds, gridExtent, backTransform, baseCRS, crs, cellSize).tupled >>= {
      case (closestTiffOverview, gridBounds, gridExtent, backTransform, baseCRS, crs, cellSize) =>
        UnsafeLift[F].apply {
          val geoTiffTile = closestTiffOverview.tile.asInstanceOf[GeoTiffMultibandTile]
          val intersectingWindows = {
            for {
              queryPixelBounds  <- bounds
              targetPixelBounds <- queryPixelBounds.intersection(gridBounds)
            } yield {
              val targetExtent = gridExtent.extentFor(targetPixelBounds)
              val targetRasterExtent = RasterExtent(
                extent = targetExtent,
                cols = targetPixelBounds.width.toInt,
                rows = targetPixelBounds.height.toInt
              )

              // buffer the targetExtent to read a buffered area from the source tiff
              // so the resample would behave properly on borders
              val bufferedTargetExtent = targetExtent.buffer(cellSize.width, cellSize.height)

              // A tmp workaround for https://github.com/locationtech/proj4j/pull/29
              // Stacktrace details: https://github.com/geotrellis/geotrellis-contrib/pull/206#pullrequestreview-260115791
              val sourceExtent      = Proj4Transform.synchronized(bufferedTargetExtent.reprojectAsPolygon(backTransform, 0.001).getEnvelopeInternal)
              val sourcePixelBounds = closestTiffOverview.rasterExtent.gridBoundsFor(sourceExtent)
              (sourcePixelBounds, targetRasterExtent)
            }
          }.toMap

          geoTiffTile
            .crop(intersectingWindows.keys.toSeq, bands.toArray)
            .map { case (sourcePixelBounds, tile) =>
              val targetRasterExtent = intersectingWindows(sourcePixelBounds)
              val sourceRaster       = Raster(tile, closestTiffOverview.rasterExtent.extentFor(sourcePixelBounds))
              val rr                 = implicitly[RasterRegionReproject[MultibandTile]]
              rr.regionReproject(
                sourceRaster,
                baseCRS,
                crs,
                targetRasterExtent,
                targetRasterExtent.extent.toPolygon(),
                resampleMethod,
                errorThreshold
              )
            }
            .map(convertRaster)
        }
    }

  def reprojection(
      targetCRS: CRS,
      resampleTarget: ResampleTarget = DefaultTarget,
      method: ResampleMethod = ResampleMethod.DEFAULT,
      strategy: OverviewStrategy = OverviewStrategy.DEFAULT
  ): RasterSourceF[F] =
    GeoTiffReprojectRasterSource(dataPath, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType, baseTiff = tiffF.some)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): RasterSourceF[F] =
    GeoTiffReprojectRasterSource(dataPath, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType, baseTiff = tiffF.some)

  def convert(targetCellType: TargetCellType): RasterSourceF[F] =
    GeoTiffReprojectRasterSource(dataPath, targetCRS, resampleTarget, resampleMethod, strategy, targetCellType = targetCellType.some)

  override def toString: String = s"GeoTiffReprojectRasterSource[F](${dataPath.value}, $crs, $resampleTarget, $resampleMethod)"
}
