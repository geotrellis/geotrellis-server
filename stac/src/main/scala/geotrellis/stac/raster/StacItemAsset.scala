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

import geotrellis.stac._
import com.azavea.stac4s.{StacAsset, StacItem}
import geotrellis.proj4.CRS
import geotrellis.raster.{CellSize, Dimensions, GridExtent, RasterExtent}
import geotrellis.stac.extensions.proj.ProjTransform
import geotrellis.vector.{Extent, Geometry}

case class StacItemAsset(itemAsset: StacAsset, item: StacItem) {
  def href: String           = itemAsset.href
  def bandCount: Option[Int] = item.bandCount
  def crs: Option[CRS]       = itemAsset.crs orElse item.crs

  // geometry can be taken from the proj extension or projected from the LatLng geometry
  def getGeometry: Option[Geometry] = itemAsset.getGeometry orElse item.getGeometry
  def getExtent: Option[Extent]     = itemAsset.getExtent orElse item.getExtent

  def transform: Option[ProjTransform] = itemAsset.transform orElse item.transform

  // https://github.com/radiantearth/stac-spec/blob/v1.0.0-rc.1/item-spec/common-metadata.md#gsd
  def gsd: Option[Double] = itemAsset.gsd orElse item.gsd

  // the cellSize can be extracted from the transform object or derived from the given extent and shape
  def cellSize: Option[CellSize] = itemAsset.cellSize orElse item.cellSize

  def gridExtent: Option[GridExtent[Long]] = itemAsset.gridExtent orElse item.gridExtent
  def rasterExtent: Option[RasterExtent]   = itemAsset.rasterExtent orElse item.rasterExtent
  def dimensions: Option[Dimensions[Long]] = itemAsset.dimensions orElse item.dimensions
}
