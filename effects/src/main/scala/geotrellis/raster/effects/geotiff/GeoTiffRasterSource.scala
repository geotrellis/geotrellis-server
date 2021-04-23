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
import geotrellis.raster.io.geotiff.{MultibandGeoTiff, GeoTiffMultibandTile, OverviewStrategy, Tags}
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.raster.resample.ResampleMethod
import geotrellis.vector._
import geotrellis.util.RangeReader

import cats._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.syntax.apply._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.option._
import cats.instances.list._

case class GeoTiffRasterSource[F[_]: Monad: UnsafeLift](
  dataPath: GeoTiffPath,
  private[raster] val targetCellType: Option[TargetCellType] = None,
  @transient private[raster] val baseTiff: Option[F[MultibandGeoTiff]] = None
) extends RasterSourceF[F] {
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

  lazy val gridExtent: F[GridExtent[Long]] = tiffF.map(_.rasterExtent.toGridType[Long])
  lazy val resolutions: F[List[CellSize]] = tiffF.map { tiff => tiff.cellSize :: tiff.overviews.map(_.cellSize) }

  def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget = DefaultTarget, method: ResampleMethod = ResampleMethod.DEFAULT, strategy: OverviewStrategy = OverviewStrategy.DEFAULT): GeoTiffReprojectRasterSource[F] =
    GeoTiffReprojectRasterSource(dataPath, targetCRS, resampleTarget, method, strategy, targetCellType = targetCellType, baseTiff = tiffF.some)

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): GeoTiffResampleRasterSource[F] =
    GeoTiffResampleRasterSource(dataPath, resampleTarget, method, strategy, targetCellType, tiffF.some)

  def convert(targetCellType: TargetCellType): GeoTiffRasterSource[F] =
    GeoTiffRasterSource(dataPath, Some(targetCellType), tiffF.some)

  def read(extent: Extent, bands: Seq[Int]): F[Raster[MultibandTile]] =
    (tiffF, gridExtent).tupled >>= { case (tiff, gridExtent) =>
      val bounds = gridExtent.gridBoundsFor(extent, clamp = false).toGridType[Int]
      val geoTiffTile = tiff.tile.asInstanceOf[GeoTiffMultibandTile]

      UnsafeLift[F].apply {
        val it = geoTiffTile.crop(List(bounds), bands.toArray).map { case (gb, tile) =>
          // TODO: shouldn't GridExtent give me Extent for types other than N ?
          Raster(tile, gridExtent.extentFor(gb.toGridType[Long], clamp = false))
        }

        // We want to use this tiff in different `RasterSource`s, so we
        // need to lock it in order to garuntee the state of tiff when
        // it's being accessed by a thread.
        tiff.synchronized {
          if (it.isEmpty) throw new Exception("The requested extent has no intersections with the actual RasterSource")
          else convertRaster(it.next)
        }
      }
    }

  def read(bounds: GridBounds[Long], bands: Seq[Int]): F[Raster[MultibandTile]] =
    readBounds(List(bounds), bands) >>= (iter => tiffF.map { _.synchronized(iter.next) })

  override def readExtents(extents: Traversable[Extent], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] = {
    val bounds: F[List[GridBounds[Long]]] = extents.toList.traverse { e => gridExtent.map(_.gridBoundsFor(e, clamp = true)) }
    bounds >>= (readBounds(_, bands))
  }

  override def readBounds(bounds: Traversable[GridBounds[Long]], bands: Seq[Int]): F[Iterator[Raster[MultibandTile]]] =
    (tiffF, gridBounds, gridExtent).tupled >>= { case (tiff, gridBounds, gridExtent) =>
      val geoTiffTile = tiff.tile.asInstanceOf[GeoTiffMultibandTile]
      val intersectingBounds: Seq[GridBounds[Int]] =
        bounds.flatMap(_.intersection(gridBounds)).toSeq.map(_.toGridType[Int])

      UnsafeLift[F].apply {
        geoTiffTile.crop(intersectingBounds, bands.toArray).map { case (gb, tile) =>
          convertRaster(Raster(tile, gridExtent.extentFor(gb.toGridType[Long], clamp = true)))
        }
      }
    }
}

