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

import geotrellis.raster.SourceName
import geotrellis.proj4.CRS
import geotrellis.raster.{CellSize, CellType, GridBounds, GridExtent}
import geotrellis.vector.Extent

import cats._
import cats.syntax.functor._
import cats.syntax.apply._

abstract class RasterMetadataF[F[_]: Monad] {
  def name: SourceName
  def crs: F[CRS]
  def bandCount: F[Int]
  def cellType: F[CellType]
  def size: F[Long] = (cols, rows).mapN(_ * _)
  def dimensions: F[(Long, Long)] = (cols, rows).mapN((c, r) => (c, r))
  def gridBounds: F[GridBounds[Long]] = (cols, rows).mapN { case (c, r) => GridBounds(0, 0, c - 1, r - 1) }
  def cellSize: F[CellSize] = gridExtent.map(_.cellSize)
  def gridExtent: F[GridExtent[Long]]
  def resolutions: F[List[CellSize]]
  def extent: F[Extent] = gridExtent.map(_.extent)
  def cols: F[Long] = gridExtent.map(_.cols)
  def rows: F[Long] = gridExtent.map(_.rows)

  /**
   * Return the "base" metadata, usually it is a zero band metadata, a metadata that is valid for the entire source and for the zero band
   */
  def attributes: F[Map[String, String]]

  /**
   * Return a per band metadata
   */
  def attributesForBand(band: Int): F[Map[String, String]]
}
