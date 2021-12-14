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

import geotrellis.stac.raster.{StacAssetOps, StacItemOps}
import geotrellis.raster.{EmptyName, GridExtent, MosaicRasterSource, RasterSource, SourceName, StringName}
import geotrellis.proj4.CRS
import geotrellis.vector.{io => _, _}

import com.azavea.stac4s.{ItemProperties, SpatialExtent, StacAsset, StacCollection, StacExtent, StacItem, TwoDimBbox}
import cats.syntax.either._
import cats.syntax.semigroup._
import cats.data.NonEmptyList
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.extras.Configuration

package object stac {
  implicit lazy val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  implicit class ExtentOps(val self: Extent) extends AnyVal {
    def toTwoDimBbox: TwoDimBbox = {
      val Extent(xmin, xmax, ymin, ymax) = self
      TwoDimBbox(xmin, xmax, ymin, ymax)
    }
  }

  implicit class SpatialExtentOps(val self: SpatialExtent) extends AnyVal {
    def toExtent: Extent                               = self.bbox.reduce(_ |+| _).toExtent.valueOr(e => throw new Exception(e))
    def expandToInclude(extent: Extent): SpatialExtent = SpatialExtent(toExtent.expandToInclude(extent).toTwoDimBbox :: Nil)
  }

  implicit class StacExtentOps(val self: StacExtent) extends AnyVal {
    def expandToInclude(extent: Extent): StacExtent = self.copy(spatial = self.spatial.expandToInclude(extent))
  }

  implicit def stacItemOps(stacItem: StacItem): StacItemOps     = StacItemOps(stacItem)
  implicit def stacAssetOps(stacAsset: StacAsset): StacAssetOps = StacAssetOps(stacAsset)

  implicit class StacCollectionOps(val self: StacCollection) extends AnyVal {
    def sourceName: SourceName                                = StringName(self.id)
    def expandExtentToInclude(extent: Extent): StacCollection = self.copy(extent = self.extent.expandToInclude(extent))
  }

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

  implicit class ItemPropertiesOps(val self: ItemProperties) extends AnyVal {
    def toMap: Map[String, String] = self.asJson.asObject
      .map(_.toMap)
      .getOrElse(Map.empty[String, Json])
      .mapValues(_.as[String].toOption)
      .collect { case (k, v) if v.nonEmpty => k -> v.get }
  }
}
