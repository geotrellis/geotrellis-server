/*
 * Copyright 2020 Azavea
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

package geotrellis.store.query

import geotrellis.proj4.CRS
import geotrellis.vector.{Extent, ProjectedExtent}
import geotrellis.raster.io.geotiff.OverviewStrategy
import geotrellis.raster._

import java.time.ZonedDateTime

case class EmptyRasterSource(identifier: String, projectedExtent: ProjectedExtent, time: Option[ZonedDateTime]) extends RasterSource {

  def targetCellType: Option[TargetCellType] = None

  def metadata: RasterMetadata = EmptyMetadata(name, crs, bandCount, cellType, gridExtent, resolutions, attributes)

  def reprojection(targetCRS: CRS, resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): RasterSource = throw new UnsupportedOperationException

  def resample(resampleTarget: ResampleTarget, method: ResampleMethod, strategy: OverviewStrategy): RasterSource = throw new UnsupportedOperationException

  def read(extent: Extent, bands: Seq[Int]): Option[Raster[MultibandTile]] = throw new UnsupportedOperationException

  def read(bounds: GridBounds[Long], bands: Seq[Int]): Option[Raster[MultibandTile]] = throw new UnsupportedOperationException

  def convert(targetCellType: TargetCellType): RasterSource = throw new UnsupportedOperationException

  def name: SourceName = StringName(identifier)

  def crs: CRS = projectedExtent.crs

  def bandCount: Int = 0

  def cellType: CellType = IntCellType

  def gridExtent: GridExtent[Long] = GridExtent(projectedExtent.extent, CellSize(1, 1))

  def resolutions: List[CellSize] = Nil

  def attributes: Map[String, String] = time.fold(Map.empty[String, String])(t => Map("time" -> t.toString))

  def attributesForBand(band: Int): Map[String, String] = Map.empty
}
