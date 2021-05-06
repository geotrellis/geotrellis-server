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

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import geotrellis.raster.{MosaicRasterSource => MosaicRasterSourceS, _}
import geotrellis.vector._
import geotrellis.raster.resample._
import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy
import cats.syntax.parallel._
import cats.syntax.flatMap._
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import geotrellis.store.util.BlockingThreadPool

/** Single threaded instance of a reader for reading windows out of collections
  * of rasters
  *
  * @param sources The underlying [[RasterSource]]s that you'll use for data access
  * @param crs The crs to reproject all [[RasterSource]]s to anytime we need information about their data
  * Since MosaicRasterSources represent collections of [[RasterSource]]s, we don't know in advance
  * whether they'll have the same CRS. crs allows specifying the CRS on read instead of
  * having to make sure at compile time that you're threading CRSes through everywhere correctly.
  */
abstract class MosaicRasterSourceIO extends RasterSource {

  implicit lazy val cs: ContextShift[IO] = IO.contextShift(BlockingThreadPool.executionContext)
  implicit val logger: Logger[IO]        = Slf4jLogger.getLogger

  def sources: NonEmptyList[RasterSource]
  lazy val sourcesIO: NonEmptyList[IO[RasterSource]] = sources.map(cs.shift >> IO(_))
  def crs: CRS
  def gridExtent: GridExtent[Long]

  import MosaicRasterSourceS._

  val targetCellType = None

  /** The bandCount of the first [[RasterSource]] in sources
    *
    * If this value is larger than the bandCount of later [[RasterSource]]s in sources,
    * reads of all bands will fail. It is a client's responsibility to construct
    * mosaics that can be read.
    */
  def bandCount: Int = sources.head.bandCount

  def cellType: CellType =
    sourcesIO
      .parTraverse(_.flatMap { rs =>
        logger
          .trace(s"${rs.name}::cellType: ${Thread.currentThread().getName}")
          .as(rs.cellType)
      })
      .map(_.toList.reduce(_ union _))
      .unsafeRunSync()

  /** All available RasterSources metadata. */
  def metadata: MosaicMetadata = MosaicMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, sources)

  def attributes: Map[String, String] = Map.empty

  def attributesForBand(band: Int): Map[String, String] = Map.empty

  /** All available resolutions for all RasterSources in this MosaicRasterSourceFixed
    *
    * @see [[geotrellis.raster.RasterSource.resolutions]]
    */
  def resolutions: List[CellSize] =
    sourcesIO
      .parTraverse(_.flatMap { rs =>
        logger
          .trace(s"${rs.name}::resolutions: ${Thread.currentThread().getName}")
          .as(rs.resolutions)
      })
      .map(_.reduce)
      .unsafeRunSync()

  /** Create a new MosaicRasterSourceFixed with sources transformed according to the provided
    * crs, options, and strategy, and a new crs
    *
    * @see [[geotrellis.raster.RasterSource.reproject]]
    */
  def reprojection(
    targetCRS: CRS,
    resampleTarget: ResampleTarget = DefaultTarget,
    method: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT
  ): RasterSource =
    MosaicRasterSourceIO
      .instance(
        sourcesIO.parTraverse(_.map(_.reproject(targetCRS, resampleTarget, method, strategy))).unsafeRunSync(),
        targetCRS,
        name,
        attributes
      )

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    sourcesIO
      .parTraverse {
        _.flatMap { rs =>
          logger
            .trace(s"${rs.name}::read($extent, $bands): ${Thread.currentThread().getName}")
            .as(rs.read(extent, bands))
        }
      }
      .map(_.reduce)
      .unsafeRunSync()

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] = {

    /** The passed bounds are relative to the [[MosaicRasterSourceIO]] bounds.
      * However, each [[RasterSource]] has its own [[GridBounds]].
      * Before passing [[GridBounds]] into each underlying [[RasterSource]]
      * we need to map them into the each [[RasterSource]] relative grid space.
      *
      * This is done by calculating the relative offset using each [[RasterSource]]
      * underlying [[Extent]].
      *
      * @param gb     global bounds, relative to the [[MosaicRasterSourceIO]]
      * @param extent extent of the [[RasterSource]]
      * @return       relative to the extent [[GridBounds]]
      */
    def relativeGridBounds(gb: GridBounds[Long], extent: Extent): GridBounds[Long] = {
      val GridBounds(colMin, rowMin, colMax, rowMax) = gb
      val (sourceColOffset, sourceRowOffset)         = gridExtent.mapToGrid(extent.xmin, extent.ymax)

      GridBounds(
        colMin - sourceColOffset,
        rowMin - sourceRowOffset,
        colMax - sourceColOffset,
        rowMax - sourceRowOffset
      )
    }

    sourcesIO
      .parTraverse {
        _.flatMap { rs =>
          logger
            .trace(s"${rs.name}::read($bounds, $bands): ${Thread.currentThread().getName}")
            .as(rs.read(relativeGridBounds(bounds, rs.extent), bands))
        }
      }
      .map(_.reduce)
      .unsafeRunSync()
  }

  def resample(
    resampleTarget: ResampleTarget,
    method: ResampleMethod,
    strategy: OverviewStrategy
  ): RasterSource =
    MosaicRasterSourceIO
      .instance(
        sourcesIO.parTraverse(_.map(_.resample(resampleTarget, method, strategy))).unsafeRunSync(),
        crs,
        name,
        attributes
      )

  def convert(targetCellType: TargetCellType): RasterSource =
    MosaicRasterSourceIO
      .instance(
        sourcesIO.parTraverse(_.map(_.convert(targetCellType))).unsafeRunSync(),
        crs,
        name,
        attributes
      )

  override def toString: String = s"MosaicRasterSourceFixed(${sources.toList}, $crs, $gridExtent, $name)"
}

object MosaicRasterSourceIO {

  /** This instance method allows to create an instance of the MosaicRasterSource.
    * It assumes that all raster sources are known, and the GridExtent is also known.
    */
  def instance(
    sourcesList: NonEmptyList[RasterSource],
    targetCRS: CRS,
    targetGridExtent: GridExtent[Long],
    sourceName: SourceName,
    stacAttributes: Map[String, String]
  ): MosaicRasterSourceIO =
    new MosaicRasterSourceIO {
      val sources: NonEmptyList[RasterSource]      = sourcesList
      val crs: CRS                                 = targetCRS
      val gridExtent: GridExtent[Long]             = targetGridExtent
      val name: SourceName                         = sourceName
      override val attributes: Map[String, String] = stacAttributes
    }

  /** This instance method allows to create an instance of the MosaicRasterSourceFixed.
    * It computes the MosaicRasterSourceFixed basing on the input sourcesList.
    */
  def instance(
    sourcesList: NonEmptyList[RasterSource],
    targetCRS: CRS,
    sourceName: SourceName,
    stacAttributes: Map[String, String]
  ): MosaicRasterSourceIO = {
    val combinedExtent     = sourcesList.map(_.extent).toList.reduce(_ combine _)
    val minCellSize        = sourcesList.map(_.cellSize).toList.maxBy(_.resolution)
    val combinedGridExtent = GridExtent[Long](combinedExtent, minCellSize)
    instance(sourcesList, targetCRS, combinedGridExtent, sourceName, stacAttributes)
  }

  def instance(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS): MosaicRasterSourceIO =
    instance(sourcesList, targetCRS, EmptyName, Map.empty)

  def instance(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS, targetGridExtent: GridExtent[Long]): MosaicRasterSourceIO =
    instance(sourcesList, targetCRS, targetGridExtent, EmptyName, Map.empty)

  /** All apply methods reproject the input sourcesList to the targetGridExtent */
  def apply(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS, targetGridExtent: GridExtent[Long]): MosaicRasterSourceIO =
    apply(sourcesList, targetCRS, targetGridExtent, EmptyName)

  def apply(
    sourcesList: NonEmptyList[RasterSource],
    targetCRS: CRS,
    targetGridExtent: GridExtent[Long],
    rasterSourceName: SourceName
  ): MosaicRasterSourceIO = {
    new MosaicRasterSourceIO {
      val name         = rasterSourceName
      lazy val sources = sourcesList.map(cs.shift >> IO(_)).parTraverse(_.map(_.reprojectToGrid(targetCRS, gridExtent))).unsafeRunSync()
      val crs          = targetCRS

      val gridExtent: GridExtent[Long] = targetGridExtent
    }
  }

  def apply(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS): MosaicRasterSourceIO =
    apply(sourcesList, targetCRS, EmptyName)

  def apply(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS, rasterSourceName: SourceName): MosaicRasterSourceIO = {
    new MosaicRasterSourceIO {
      val name    = rasterSourceName
      val sources = sourcesList.map(cs.shift >> IO(_)).parTraverse(_.map(_.reprojectToGrid(targetCRS, sourcesList.head.gridExtent))).unsafeRunSync()
      val crs     = targetCRS
      def gridExtent: GridExtent[Long] = {
        val reprojectedSources = sourcesIO.toList
        val combinedExtent     = reprojectedSources.parTraverse(_.map(_.extent)).map(_.reduce(_ combine _)).unsafeRunSync()
        val minCellSize        = reprojectedSources.parTraverse(_.map(_.cellSize)).map(_.maxBy(_.resolution)).unsafeRunSync()
        GridExtent[Long](combinedExtent, minCellSize)
      }
    }
  }
}
