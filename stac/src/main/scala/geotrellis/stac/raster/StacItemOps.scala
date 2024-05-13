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

package geotrellis.stac.raster

import geotrellis.stac.extensions.proj.{ProjItemExtension, ProjTransform}
import geotrellis.raster.{CellSize, Dimensions, GridExtent, RasterExtent}
import geotrellis.proj4.{CRS, LatLng}
import geotrellis.vector._

import com.azavea.stac4s.StacItem
import com.azavea.stac4s.extensions.eo.EOItemExtension
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.extensions.ExtensionResult
import cats.syntax.apply._

case class StacItemOps(self: StacItem) {
  def eoExtension: ExtensionResult[EOItemExtension] = self.getExtensionFields[EOItemExtension]
  def projExtension: ExtensionResult[ProjItemExtension] = self.getExtensionFields[ProjItemExtension]

  private def eoExtensionOption: Option[EOItemExtension] = eoExtension.toOption
  private def projExtensionOption: Option[ProjItemExtension] = projExtension.toOption

  def bandCount: Option[Int] = eoExtensionOption.map(_.bands.length)
  def crs: Option[CRS] =
    projExtensionOption
      .flatMap(_.epsgCode)
      .map(CRS.fromEpsgCode)
      .orElse {
        projExtensionOption
          .flatMap(_.wktString)
          .flatMap(CRS.fromWKT)
      }

  // geometry can be taken from the proj extension or projected from the LatLng geometry
  def getGeometry: Option[Geometry] = projExtensionOption.flatMap(_.geometry).orElse(crs.map(self.geometry.reproject(LatLng, _)))
  def getExtent: Option[Extent] = getGeometry.map(geom => Extent(geom.getEnvelopeInternal))

  def transform: Option[ProjTransform] = projExtensionOption.flatMap(_.transform)

  // https://github.com/radiantearth/stac-spec/blob/v1.0.0-rc.1/item-spec/common-metadata.md#gsd
  def gsd: Option[Double] = self.properties.gsd

  // the cellSize can be extracted from the transform object or derived from the given extent and shape
  def cellSize: Option[CellSize] =
    transform
      .map(_.cellSize)
      .orElse((getExtent, dimensions).mapN { case (e, Dimensions(c, r)) =>
        GridExtent(e, c, r).cellSize
      })
      .orElse(gsd.map(d => CellSize(d, d)))

  def gridExtent: Option[GridExtent[Long]] = (getExtent, cellSize).mapN(GridExtent.apply[Long])
  def rasterExtent: Option[RasterExtent] = gridExtent.map(_.toRasterExtent)
  def dimensions: Option[Dimensions[Long]] = projExtensionOption.flatMap(_.shape).map(_.toDimensions)
}
