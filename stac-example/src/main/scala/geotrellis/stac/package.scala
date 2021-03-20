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

package geotrellis

import geotrellis.stac.raster.{StacItemAssetOps, StacItemOps}

import cats.data.NonEmptyList
import com.azavea.stac4s.{SpatialExtent, StacItem, StacItemAsset, TwoDimBbox}
import io.circe.generic.extras.Configuration
import geotrellis.raster.{EmptyName, GridExtent, MosaicRasterSource, RasterSource, SourceName}
import geotrellis.proj4.CRS
import geotrellis.vector._
import cats.syntax.either._

package object stac {
  implicit lazy val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit class ExtentOps(val self: Extent) extends AnyVal {
    def toTwoDimBbox: TwoDimBbox = {
      val Extent(xmin, xmax, ymin, ymax) = self
      TwoDimBbox(xmin, xmax, ymin, ymax)
    }
  }

  implicit class SpatialExtentOps(val self: SpatialExtent) extends AnyVal {
    // self.bbox.reduce(_ union _)
    // https://github.com/azavea/stac4s/pull/259
    def toExtent: Extent = self.bbox.head.toExtent.valueOr(e => throw new Exception(e))
  }

  implicit def stacItemOps(stacItem: StacItem): StacItemOps                     = StacItemOps(stacItem)
  implicit def stacItemAssetOps(stacItemAsset: StacItemAsset): StacItemAssetOps = StacItemAssetOps(stacItemAsset)

  implicit class MosaicRasterSourceOps(val self: MosaicRasterSource.type) extends AnyVal {
    def instance(
      sourcesList: NonEmptyList[RasterSource],
      targetCRS: CRS,
      sourceName: SourceName,
      stacAttributes: Map[String, String]
    ): MosaicRasterSource = {
      val combinedExtent     = sourcesList.map(_.extent).toList.reduce(_ combine _)
      val minCellSize        = sourcesList.map(_.cellSize).toList.maxBy(_.resolution)
      val combinedGridExtent = GridExtent[Long](combinedExtent, minCellSize)

      new MosaicRasterSource {
        val sources: NonEmptyList[RasterSource] = sourcesList
        val crs: CRS                            = targetCRS
        def gridExtent: GridExtent[Long]        = combinedGridExtent
        val name: SourceName                    = sourceName

        override val attributes = stacAttributes
      }
    }

    def instance(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS, stacAttributes: Map[String, String]): MosaicRasterSource =
      instance(sourcesList, targetCRS, EmptyName, stacAttributes)
  }
}
