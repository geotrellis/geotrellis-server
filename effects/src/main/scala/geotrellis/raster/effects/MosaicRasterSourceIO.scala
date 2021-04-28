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

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.raster.resample._
import geotrellis.proj4.CRS
import geotrellis.raster.io.geotiff.OverviewStrategy
import cats.effect.IO

/** A blocking RasterSource which works as a proxy interface between commons and effectful RasterSources. */
class MosaicRasterSourceIO(underlying: => MosaicRasterSource[IO]) extends RasterSource {
  lazy val name: SourceName             = underlying.name
  lazy val crs: CRS                     = underlying.crs.unsafeRunSync()
  lazy val gridExtent: GridExtent[Long] = underlying.gridExtent.unsafeRunSync()

  val targetCellType: Option[TargetCellType] = None
  lazy val bandCount: Int                    = underlying.bandCount.unsafeRunSync()
  lazy val cellType: CellType                = underlying.cellType.unsafeRunSync()

  lazy val metadata: MosaicMetadata = underlying.metadata.unsafeRunSync()

  def attributes: Map[String, String] = Map.empty

  def attributesForBand(band: Int): Map[String, String] = Map.empty

  lazy val resolutions: List[CellSize] = underlying.resolutions.unsafeRunSync()

  def reprojection(
    targetCRS: CRS,
    resampleTarget: ResampleTarget = DefaultTarget,
    method: ResampleMethod = ResampleMethod.DEFAULT,
    strategy: OverviewStrategy = OverviewStrategy.DEFAULT
  ): RasterSource =
    MosaicRasterSourceIO(underlying.reprojection(targetCRS))

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlying.read(extent, bands).attempt.unsafeRunSync().toOption

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] =
    underlying.read(bounds, bands).attempt.unsafeRunSync().toOption

  def resample(
    resampleTarget: ResampleTarget,
    method: ResampleMethod,
    strategy: OverviewStrategy
  ): RasterSource =
    MosaicRasterSourceIO(underlying.resample(resampleTarget, method, strategy))

  def convert(targetCellType: TargetCellType): RasterSource =
    MosaicRasterSourceIO(underlying.convert(targetCellType))

  override def toString: String = s"MosaicRasterSourceIO($underlying, $crs, $gridExtent, $name)"
}

object MosaicRasterSourceIO {
  def apply(source: MosaicRasterSource[IO]): MosaicRasterSourceIO = new MosaicRasterSourceIO(source)
}
