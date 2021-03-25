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

package geotrellis.server.ogc.wmts

import geotrellis.proj4.{CRS, LatLng, WebMercator}
import geotrellis.raster.TileLayout
import geotrellis.layer._
import geotrellis.vector.Extent

import opengis.ows._
import opengis.wmts.TileMatrix

/** Relates Geotrellis Extent and TileLayout to a corresponding OGC Tile Matrix */
case class GeotrellisTileMatrix(
  identifier: String,
  extent: Extent,
  tileLayout: TileLayout,
  title: Option[String] = None,
  `abstract`: Option[String] = None
) {
  val layout: LayoutDefinition = LayoutDefinition(extent, tileLayout)
  require(layout.cellSize.width == layout.cellSize.height, s"Layout definition cell size width must be same as height: ${layout.cellSize}")

  val projectionMetersPerUnit: Map[CRS, Double] = Map(
    // meters per unit on equator
    LatLng      -> 6378137.0 * 2.0 * math.Pi / 360.0,
    WebMercator -> 1
  )

  def toXml(crs: CRS): TileMatrix = {
    projectionMetersPerUnit.get(crs) match {
      case Some(metersPerUnit) =>
        val scaleDenominator = layout.cellSize.width / 0.00028 * metersPerUnit
        TileMatrix(
          Title = title.map(LanguageStringType(_)).toList,
          Abstract = `abstract`.map(LanguageStringType(_)).toList,
          Keywords = Nil,
          Identifier = CodeType(identifier),
          ScaleDenominator = scaleDenominator,
          TopLeftCorner = layout.extent.xmin :: layout.extent.ymax :: Nil,
          TileWidth = layout.tileLayout.tileCols,
          TileHeight = layout.tileLayout.tileRows,
          MatrixWidth = layout.tileLayout.layoutCols,
          MatrixHeight = layout.tileLayout.layoutRows
        )

      case None =>
        throw new Exception(s"Invalid CRS: ${crs}")
    }
  }
}
