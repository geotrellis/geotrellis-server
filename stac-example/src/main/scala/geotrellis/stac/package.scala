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

import cats.data.NonEmptyList
import geotrellis.stac.extensions.proj.{ProjItemExtension, ProjTransform}
import com.azavea.stac4s.{SpatialExtent, StacItemAsset, TwoDimBbox}
import com.azavea.stac4s.extensions.eo.EOItemExtension
import com.azavea.stac4s.syntax._
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import io.circe.generic.extras.Configuration
import geotrellis.raster.{CellSize, Dimensions, GridExtent, MosaicRasterSource, RasterExtent, RasterSource, SourceName}
import geotrellis.proj4.CRS
import geotrellis.vector._
import cats.syntax.either._
import cats.syntax.apply._
import com.azavea.stac4s.api.client.{SearchFilters, SttpStacClientF}

package object stac {
  type LoggableSttpStacClient[F[_]] = SttpStacClientF.Aux[F, SearchFilters]

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

  case class StacItemProperties(self: JsonObject) {
    def toMap: Map[String, Json] = self.toMap

    def eoExtension: Option[EOItemExtension] = self.asJson.as[EOItemExtension].toOption
    def projExtension: Option[ProjItemExtension] = self.asJson.as[ProjItemExtension].toOption

    def bandCount: Option[Int] = eoExtension.map(_.bands.length)
    def crs: Option[CRS] =
      projExtension
        .flatMap(_.epsgCode)
        .map(CRS.fromEpsgCode)
        .orElse {
          projExtension
            .flatMap(_.wktString)
            .flatMap(CRS.fromWKT)
        }

    // https://github.com/radiantearth/stac-spec/blob/v1.0.0-rc.1/item-spec/common-metadata.md#gsd
    def gsd: Option[Double] = self("gsd").flatMap(_.as[Double].toOption)
    def geometry: Option[Geometry] = projExtension.flatMap(_.geometry)
    def extent: Option[Extent] = geometry.map(geom => Extent(geom.getEnvelopeInternal))
    def transform: Option[ProjTransform] = projExtension.flatMap(_.transform)
    // the cellSize can be extracted from the transform object or derived from the given extent and shape
    def cellSize: Option[CellSize] = transform.map(_.cellSize).orElse((extent, dimensions).mapN {
      case (e, Dimensions(c, r)) => GridExtent(e, c, r).cellSize
    }).orElse(gsd.map(d => CellSize(d, d)))
    def gridExtent: Option[GridExtent[Long]] = (extent, cellSize).mapN(GridExtent.apply[Long])
    def rasterExtent: Option[RasterExtent] = gridExtent.map(_.toRasterExtent)
    def dimensions: Option[Dimensions[Long]] = projExtension.flatMap(_.shape).map(_.toDimensions)
  }

  object StacItemProperties {
    val EMPTY: StacItemProperties = StacItemProperties(JsonObject.empty)
  }

  implicit class StacItemAssetOps(val self: StacItemAsset) extends AnyVal {
    def projExtension: Option[ProjItemExtension] = self.getExtensionFields[ProjItemExtension].toOption

    def crs: Option[CRS] =
      projExtension
        .flatMap(_.epsgCode)
        .map(CRS.fromEpsgCode)
        .orElse {
          projExtension
            .flatMap(_.wktString)
            .flatMap(CRS.fromWKT)
        }

    // https://github.com/radiantearth/stac-spec/blob/v1.0.0-rc.1/item-spec/common-metadata.md#gsd
    def gsd: Option[Double] = self.extensionFields("gsd").flatMap(_.as[Double].toOption)
    def geometry: Option[Geometry] = projExtension.flatMap(_.geometry)
    def extent: Option[Extent] = geometry.map(geom => Extent(geom.getEnvelopeInternal))
    def transform: Option[ProjTransform] = projExtension.flatMap(_.transform)
    // the cellSize can be extracted from the transform object or derived from the given extent and shape
    def cellSize: Option[CellSize] = transform.map(_.cellSize).orElse((extent, dimensions).mapN {
      case (e, Dimensions(c, r)) => GridExtent(e, c, r).cellSize
    }).orElse(gsd.map(d => CellSize(d, d)))
    def gridExtent: Option[GridExtent[Long]] = (extent, cellSize).mapN(GridExtent.apply[Long])
    def rasterExtent: Option[RasterExtent] = gridExtent.map(_.toRasterExtent)
    def dimensions: Option[Dimensions[Long]] = projExtension.flatMap(_.shape).map(_.toDimensions)
  }

  implicit class MosaicRasterSourceOps(val self: MosaicRasterSource.type) extends AnyVal {
    def instance(sourcesList: NonEmptyList[RasterSource], targetCRS: CRS, sourceName: SourceName, stacAttributes: Map[String, String]): MosaicRasterSource = {
      val combinedExtent = sourcesList.map(_.extent).toList.reduce(_ combine _)
      val minCellSize = sourcesList.map(_.cellSize).toList.maxBy(_.resolution)
      val combinedGridExtent = GridExtent[Long](combinedExtent, minCellSize)
      self.instance(sourcesList, targetCRS, combinedGridExtent, sourceName)

      new MosaicRasterSource {
        val sources: NonEmptyList[RasterSource] = sourcesList
        val crs: CRS = targetCRS
        def gridExtent: GridExtent[Long] = combinedGridExtent
        val name: SourceName = sourceName

        override val attributes = stacAttributes
      }
    }
  }
}
