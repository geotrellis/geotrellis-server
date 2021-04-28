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
import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy

import cats._
import cats.syntax.parallel._
import cats.syntax.flatMap._
import cats.syntax.apply._
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.instances.list._
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import spire.math.Integral

/** Single threaded instance of a reader for reading windows out of collections
  * of rasters
  *
  * @param sources The underlying [[RasterSourceF]]s that you'll use for data access
  * @param crs The crs to reproject all [[RasterSourceF]]s to anytime we need information about their data
  * Since MosaicRasterSources represent collections of [[RasterSourceF]]s, we don't know in advance
  * whether they'll have the same CRS. crs allows specifying the CRS on read instead of
  * having to make sure at compile time that you're threading CRSes through everywhere correctly.
  */
abstract class MosaicRasterSource[F[_]: Monad: Parallel: MonadThrow] extends RasterSourceF[F] {
  import MosaicRasterSource._

  def sources: F[NonEmptyList[F[RasterSource]]]
  def crs: F[CRS]
  def gridExtent: F[GridExtent[Long]]

  val targetCellType: Option[TargetCellType] = None

  /** The bandCount of the first [[RasterSourceF]] in sources
    *
    * If this value is larger than the bandCount of later [[RasterSourceF]]s in sources,
    * reads of all bands will fail. It is a client's responsibility to construct
    * mosaics that can be read.
    */
  def bandCount: F[Int] = sources >>= { sources => sources.head.map { _.bandCount } }

  def cellType: F[CellType] =
    sources
      .flatMap(_.parTraverse(_.map(_.cellType)))
      .map(_.toList.reduce(_ union _))

  /** All available RasterSources metadata. */
  def metadata: F[MosaicMetadata] = (
    name.pure[F],
    crs,
    bandCount,
    cellType,
    gridExtent,
    resolutions,
    sources >>= { _.parTraverse(_.map(_.metadata)) }
  ).mapN { MosaicMetadata }

  def attributes: F[Map[String, String]] = Map.empty[String, String].pure[F]

  def attributesForBand(band: Int): F[Map[String, String]] = Map.empty[String, String].pure[F]

  /** All available resolutions for all RasterSources in this MosaicRasterSource
    *
    * @see [[geotrellis.raster.RasterSource.resolutions]]
    */
  def resolutions: F[List[CellSize]] = {
    val resolutions: F[NonEmptyList[List[CellSize]]] = sources >>= (_.parTraverse(_.map(_.resolutions)))
    resolutions.map(_.reduce)
  }

  /** Create a new MosaicRasterSource with sources transformed according to the provided
    * crs, options, and strategy, and a new crs
    *
    * @see [[geotrellis.raster.RasterSource.reproject]]
    */
  def reprojection(
    targetCRS: CRS,
    resampleTarget: ResampleTarget = DefaultTarget,
    method: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT
  ): MosaicRasterSource[F] =
    MosaicRasterSource.instance(sources.map(_.map(_.map(_.reproject(targetCRS, resampleTarget, method, strategy)))), targetCRS.pure[F], name)

  def read(extent: Extent, bands: Seq[Int]): F[Raster[MultibandTile]] = {
    sources
      .flatMap(_.parTraverse { s =>
        println("------------")
        println(Thread.currentThread().getName)
        println("------------")
        s.map { ss =>
          println("~~~~~~~~~~~~")
          println(Thread.currentThread().getName)
          println("~~~~~~~~~~~~")
          ss.read(extent, bands)
        }
      }.map(_.reduce))
      .map { o =>
        o.map { r =>
          println(s"r.tile.band(0).findMinMaxDouble: ${r.tile.band(0).findMinMaxDouble}")
        }
        o
      }
      .flatMap(MonadError[F, Throwable].fromOption(_, throw new Exception("Empty result"))) // TODO: fixme
  }

  def read(bounds: GridBounds[Long], bands: Seq[Int]): F[Raster[MultibandTile]] = {

    /** The passed bounds are relative to the [[MosaicRasterSource]] bounds.
      * However, each [[RasterSource]] has its own [[GridBounds]].
      * Before passing [[GridBounds]] into each underlying [[RasterSource]]
      * we need to map them into the each [[RasterSource]] relative grid space.
      *
      * This is done by calculating the relative offset using each [[RasterSource]]
      * underlying [[Extent]].
      *
      * @param gb     global bounds, relative to the [[MosaicRasterSource]]
      * @param extent extent of the [[RasterSource]]
      * @return       relative to the extent [[GridBounds]]
      */
    def relativeGridBounds(gb: GridBounds[Long], extent: Extent): F[GridBounds[Long]] = {
      val GridBounds(colMin, rowMin, colMax, rowMax) = gb
      gridExtent.map(_.mapToGrid(extent.xmin, extent.ymax)).map { case (sourceColOffset, sourceRowOffset) =>
        GridBounds(
          colMin - sourceColOffset,
          rowMin - sourceRowOffset,
          colMax - sourceColOffset,
          rowMax - sourceRowOffset
        )
      }
    }

    sources
      .flatMap {
        _.parTraverse { rs =>
          rs
            .map(_.extent)
            .flatMap(relativeGridBounds(bounds, _))
            .flatMap(bounds => rs.map(_.read(bounds)))
            .flatMap(MonadError[F, Throwable].fromOption(_, throw new Exception("Empty result"))) // TODO: fixme
        }
      }
      .map(_.reduce)
  }

  def resample(
    resampleTarget: ResampleTarget,
    method: ResampleMethod,
    strategy: OverviewStrategy
  ): MosaicRasterSource[F] =
    MosaicRasterSource.instance(sources.map(_.map(_.map(_.resample(resampleTarget, method, strategy)))), crs, name)

  def convert(targetCellType: TargetCellType): MosaicRasterSource[F] =
    MosaicRasterSource.instance(sources.map(_.map(_.map(_.convert(targetCellType)))), crs, name)

  override def toString: String = s"MosaicRasterSource[F]($sources, $crs, $gridExtent, $name)"
}

object MosaicRasterSource {
  implicit val rasterSemigroup: Semigroup[Raster[MultibandTile]]          = MosaicRasterSourceF.rasterSemigroup
  implicit def gridExtentSemigroup[N: Integral]: Semigroup[GridExtent[N]] = MosaicRasterSourceF.gridExtentSemigroup

  /** This instance method allows to create an instance of the MosaicRasterSource.
    * It assumes that all raster sources are known, and the GridExtent is also known.
    */
  def instanceGridExtent[F[_]: Monad: Parallel: MonadThrow](
    sourcesList: F[NonEmptyList[F[RasterSource]]],
    targetCRS: F[CRS],
    targetGridExtent: F[GridExtent[Long]],
    sourceName: SourceName
  ): MosaicRasterSource[F] =
    new MosaicRasterSource[F] {
      val sources: F[NonEmptyList[F[RasterSource]]] = sourcesList
      val crs: F[CRS]                               = targetCRS
      def gridExtent: F[GridExtent[Long]]           = targetGridExtent
      val name: SourceName                          = sourceName
    }

  /** This instance method allows to create an instance of the MosaicRasterSource.
    * It computes the MosaicRasterSource basing on the input sourcesList.
    */
  def instance[F[_]: Monad: Parallel: MonadThrow](
    sourcesList: F[NonEmptyList[F[RasterSource]]],
    targetCRS: F[CRS],
    sourceName: SourceName = EmptyName
  ): MosaicRasterSource[F] = {
    val combinedExtent     = sourcesList >>= { _.parTraverse(_.map(_.extent)).map(_.toList.reduce(_ combine _)) }
    val minCellSize        = sourcesList >>= { _.parTraverse(_.map(_.cellSize)).map(_.toList.maxBy(_.resolution)) }
    val combinedGridExtent = (combinedExtent, minCellSize).mapN(GridExtent[Long])
    instanceGridExtent(sourcesList, targetCRS, combinedGridExtent, sourceName)
  }

  /** All apply methods reproject the input sourcesList to the targetGridExtent */
  def applyGridExtent[F[_]: Monad: Parallel: MonadThrow](
    sourcesList: F[NonEmptyList[F[RasterSource]]],
    targetCRS: F[CRS],
    targetGridExtent: F[GridExtent[Long]],
    rasterSourceName: SourceName = EmptyName
  ): MosaicRasterSource[F] = {
    new MosaicRasterSource[F] {
      val name: SourceName                          = rasterSourceName
      val sources: F[NonEmptyList[F[RasterSource]]] =
        (targetCRS, gridExtent).tupled >>= { case (targetCRS, targetGridExtent) =>
          sourcesList.map(_.map(_.map(_.reprojectToGrid(targetCRS, targetGridExtent))))
        }
      val crs: F[CRS]                               = targetCRS

      def gridExtent: F[GridExtent[Long]] = targetGridExtent
    }
  }

  def apply[F[_]: Monad: Parallel: MonadThrow](
    sourcesList: F[NonEmptyList[F[RasterSource]]],
    targetCRS: F[CRS],
    rasterSourceName: SourceName
  ): MosaicRasterSource[F] = {
    new MosaicRasterSource[F] {
      val name: SourceName                          = rasterSourceName
      val sources: F[NonEmptyList[F[RasterSource]]] =
        (targetCRS, sourcesList.flatMap(_.head.map(_.gridExtent))).tupled >>= { case (crs, ge) =>
          sourcesList.map(_.map(_.map(_.reprojectToGrid(crs, ge))))
        }
      val crs: F[CRS]                               = targetCRS
      def gridExtent: F[GridExtent[Long]] = {
        val combinedExtent: F[Extent] = sources.flatMap(_.parTraverse(_.map(_.extent))).map(_.reduceLeft(_ combine _))
        val minCellSize: F[CellSize]  = sources.flatMap(_.parTraverse(_.map(_.cellSize))).map(_.toList.maxBy(_.resolution))
        (combinedExtent, minCellSize).mapN(GridExtent[Long])
      }
    }
  }

  def instanceIO[F[_]: Monad: Parallel: MonadThrow](
    sourcesList: F[NonEmptyList[F[RasterSource]]],
    targetCRS: F[CRS],
    sourceName: SourceName,
    stacAttributes: F[Map[String, String]]
  ): MosaicRasterSource[IO] = {
    val combinedExtent: F[Extent]               = sourcesList.flatMap(_.parTraverse(_.map(_.extent))).map(_.reduceLeft(_ combine _))
    val minCellSize: F[CellSize]                = sourcesList.flatMap(_.parTraverse(_.map(_.cellSize))).map(_.toList.maxBy(_.resolution))
    val combinedGridExtent: F[GridExtent[Long]] = (combinedExtent, minCellSize).mapN(GridExtent[Long])
    val source                                  = new MosaicRasterSource[F] {
      val sources: F[NonEmptyList[F[RasterSource]]] = sourcesList
      val crs: F[CRS]                               = targetCRS
      val gridExtent: F[GridExtent[Long]]           = combinedGridExtent
      val name: SourceName                          = sourceName

      override val attributes: F[Map[String, String]] = stacAttributes
    }

    source.asInstanceOf[MosaicRasterSource[IO]]
  }
}
