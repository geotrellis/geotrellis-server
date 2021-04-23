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
abstract class MosaicRasterSourceF[F[_]: Monad: Parallel] extends RasterSourceF[F] {
  import MosaicRasterSourceF._

  val sources: F[NonEmptyList[RasterSourceF[F]]]
  val crs: F[CRS]
  def gridExtent: F[GridExtent[Long]]

  val targetCellType: Option[TargetCellType] = None

  /** The bandCount of the first [[RasterSourceF]] in sources
    *
    * If this value is larger than the bandCount of later [[RasterSourceF]]s in sources,
    * reads of all bands will fail. It is a client's responsibility to construct
    * mosaics that can be read.
    */
  def bandCount: F[Int] = sources >>= (_.head.bandCount)

  def cellType: F[CellType] = {
    val cellTypes = sources >>= (_.parTraverse(_.cellType))
    cellTypes >>= { _.tail.foldLeft(cellTypes.map(_.head)) { (l, r) => (l, r.pure[F]).mapN(_ union _) } }
  }

  /** All available RasterSources metadata. */
  def metadata: F[MosaicMetadata] = (
    name.pure[F],
    crs,
    bandCount,
    cellType,
    gridExtent,
    resolutions,
    sources >>= { _.parTraverse(_.metadata.map(md => md: RasterMetadata)) }
  ).mapN { MosaicMetadata }

  def attributes: F[Map[String, String]] = Map.empty[String, String].pure[F]

  def attributesForBand(band: Int): F[Map[String, String]] = Map.empty[String, String].pure[F]

  /** All available resolutions for all RasterSources in this MosaicRasterSourceF
    *
    * @see [[geotrellis.raster.RasterSource.resolutions]]
    */
  def resolutions: F[List[CellSize]] = {
    val resolutions: F[NonEmptyList[List[CellSize]]] = sources >>= (_.parTraverse(_.resolutions))
    resolutions.map(_.reduce)
  }

  /** Create a new MosaicRasterSourceF with sources transformed according to the provided
    * crs, options, and strategy, and a new crs
    *
    * @see [[geotrellis.raster.RasterSource.reproject]]
    */
  def reprojection(
    targetCRS: CRS,
    resampleTarget: ResampleTarget = DefaultTarget,
    method: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT
  ): RasterSourceF[F] =
    MosaicRasterSourceF.instance(sources.map(_.map(_.reproject(targetCRS, resampleTarget, method, strategy))), targetCRS.pure[F], name)

  def read(extent: Extent, bands: Seq[Int]): F[Raster[MultibandTile]] =
    sources >>= (_.parTraverse { _.read(extent, bands) }.map(_.reduce))

  def read(bounds: GridBounds[Long], bands: Seq[Int]): F[Raster[MultibandTile]] = {

    /** The passed bounds are relative to the [[MosaicRasterSourceF]] bounds.
      * However, each [[RasterSource]] has its own [[GridBounds]].
      * Before passing [[GridBounds]] into each underlying [[RasterSource]]
      * we need to map them into the each [[RasterSource]] relative grid space.
      *
      * This is done by calculating the relative offset using each [[RasterSource]]
      * underlying [[Extent]].
      *
      * @param gb     global bounds, relative to the [[MosaicRasterSourceF]]
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

    (sources >>= { _.parTraverse { rs => rs.extent.flatMap(relativeGridBounds(bounds, _)).flatMap(rs.read) } }).map(_.reduce)
  }

  def resample(
    resampleTarget: ResampleTarget,
    method: ResampleMethod,
    strategy: OverviewStrategy
  ): RasterSourceF[F] =
    MosaicRasterSourceF.instance(sources.map(_.map(_.resample(resampleTarget, method, strategy))), crs, name)

  def convert(targetCellType: TargetCellType): RasterSourceF[F] = {
    MosaicRasterSourceF.instance(sources.map(_.map(_.convert(targetCellType))), crs, name)
  }

  override def toString: String = s"MosaicRasterSourceF[F]($sources, $crs, $gridExtent, $name)"
}

object MosaicRasterSourceF {
  // Orphan instance for semigroups for rasters, so we can combine
  // Option[Raster[_]]s later
  implicit val rasterSemigroup: Semigroup[Raster[MultibandTile]] = { (l: Raster[MultibandTile], r: Raster[MultibandTile]) =>
    val targetRE =
      RasterExtent(l.rasterExtent.extent combine r.rasterExtent.extent, List(l.rasterExtent.cellSize, r.rasterExtent.cellSize).maxBy(_.resolution))
    l.resample(targetRE) merge r.resample(targetRE)
  }

  implicit def gridExtentSemigroup[N: Integral]: Semigroup[GridExtent[N]] = { (l: GridExtent[N], r: GridExtent[N]) =>
    if (l.cellwidth != r.cellwidth)
      throw GeoAttrsError(s"illegal cellwidths: ${l.cellwidth} and ${r.cellwidth}")
    if (l.cellheight != r.cellheight)
      throw GeoAttrsError(s"illegal cellheights: ${l.cellheight} and ${r.cellheight}")

    val newExtent = l.extent.combine(r.extent)
    val newRows   = Integral[N].fromDouble(math.round(newExtent.height / l.cellheight).toDouble)
    val newCols   = Integral[N].fromDouble(math.round(newExtent.width / l.cellwidth).toDouble)
    new GridExtent[N](newExtent, l.cellwidth, l.cellheight, newCols, newRows)
  }

  /** This instance method allows to create an instance of the MosaicRasterSourceF.
    * It assumes that all raster sources are known, and the GridExtent is also known.
    */
  def instanceGridExtent[F[_]: Monad: Parallel](
    sourcesList: F[NonEmptyList[RasterSourceF[F]]],
    targetCRS: F[CRS],
    targetGridExtent: F[GridExtent[Long]],
    sourceName: SourceName
  ): MosaicRasterSourceF[F] =
    new MosaicRasterSourceF[F] {
      val sources: F[NonEmptyList[RasterSourceF[F]]] = sourcesList
      val crs: F[CRS]                                = targetCRS
      def gridExtent: F[GridExtent[Long]]            = targetGridExtent
      val name: SourceName                           = sourceName
    }

  /** This instance method allows to create an instance of the MosaicRasterSourceF.
    * It computes the MosaicRasterSourceF basing on the input sourcesList.
    */
  def instance[F[_]: Monad: Parallel](
    sourcesList: F[NonEmptyList[RasterSourceF[F]]],
    targetCRS: F[CRS],
    sourceName: SourceName = EmptyName
  ): MosaicRasterSourceF[F] = {
    val combinedExtent     = sourcesList >>= { _.parTraverse(_.extent).map(_.toList.reduce(_ combine _)) }
    val minCellSize        = sourcesList >>= { _.parTraverse(_.cellSize).map(_.toList.maxBy(_.resolution)) }
    val combinedGridExtent = (combinedExtent, minCellSize).mapN(GridExtent[Long])
    instanceGridExtent(sourcesList, targetCRS, combinedGridExtent, sourceName)
  }

  /** All apply methods reproject the input sourcesList to the targetGridExtent */
  def applyGridExtent[F[_]: Monad: Parallel](
    sourcesList: F[NonEmptyList[RasterSourceF[F]]],
    targetCRS: F[CRS],
    targetGridExtent: F[GridExtent[Long]],
    rasterSourceName: SourceName = EmptyName
  ): MosaicRasterSourceF[F] = {
    new MosaicRasterSourceF[F] {
      val name: SourceName                           = rasterSourceName
      val sources: F[NonEmptyList[RasterSourceF[F]]] = (targetCRS, gridExtent).tupled >>= { case (targetCRS, targetGridExtent) =>
        sourcesList.map(_.map(_.reprojectToGrid(targetCRS, targetGridExtent)))
      }
      val crs: F[CRS]                                = targetCRS

      def gridExtent: F[GridExtent[Long]] = targetGridExtent
    }
  }

  def apply[F[_]: Monad: Parallel](
    sourcesList: F[NonEmptyList[RasterSourceF[F]]],
    targetCRS: F[CRS],
    rasterSourceName: SourceName
  ): MosaicRasterSourceF[F] = {
    new MosaicRasterSourceF[F] {
      val name: SourceName                           = rasterSourceName
      val sources: F[NonEmptyList[RasterSourceF[F]]] = (targetCRS, sourcesList.flatMap(_.head.gridExtent)).tupled >>= { case (crs, ge) =>
        sourcesList.map(_.map(_.reprojectToGrid(crs, ge)))
      }
      val crs: F[CRS]                                = targetCRS
      def gridExtent: F[GridExtent[Long]] = {
        val combinedExtent: F[Extent] = sources.flatMap(_.parTraverse(_.extent).map(_.reduceLeft(_ combine _)))
        val minCellSize: F[CellSize]  = sources.flatMap(_.parTraverse(_.cellSize).map(_.toList.maxBy(_.resolution)))
        (combinedExtent, minCellSize).mapN(GridExtent[Long])
      }
    }
  }
}
