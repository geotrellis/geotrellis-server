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

import geotrellis.server.ogc._
import geotrellis.layer._
import geotrellis.proj4._

/** This class holds all the information necessary to construct a response to a WMTS request */
case class WmtsModel(
  serviceMetadata: ows.ServiceMetadata,
  matrices: List[GeotrellisTileMatrixSet],
  sources: Seq[OgcSource]
) {

  val sourceLookup: Map[String, OgcSource] = sources.map({layer => layer.name -> layer}).toMap

  val matrixSetLookup: Map[String, GeotrellisTileMatrixSet] =
    matrices.map({tileMatrixSet => tileMatrixSet.identifier -> tileMatrixSet}).toMap

  /** Take a specific request for a map and combine it with the relevant [[OgcSource]]
   *  to produce a [[TiledOgcLayer]]
   */
  def getLayer(crs: CRS, layerName: String, layout: LayoutDefinition, styleName: String): Option[TiledOgcLayer] = {
    for {
      source <- sourceLookup.get(layerName)
    } yield {
      val style: Option[OgcStyle] = source.styles.find(_.name == styleName)
      source match {
        case MapAlgebraSource(name, title, rasterSources, algebra, styles) =>
          val simpleLayers = rasterSources.mapValues { rs =>
            SimpleTiledOgcLayer(name, title, crs, layout, rs, style)
          }
          MapAlgebraTiledOgcLayer(name, title, crs, layout, simpleLayers, algebra, style)
        case SimpleSource(name, title, rasterSource, styles) =>
          SimpleTiledOgcLayer(name, title, crs, layout, rasterSource, style)
      }
    }
  }

  def getMatrixLayoutDefinition(tileMatrixSetId: String, tileMatrixId: String): Option[LayoutDefinition] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
      matrix <- matrixSet.tileMatrix.find(_.identifier == tileMatrixId)
    } yield matrix.layout

  def getMatrixCrs(tileMatrixSetId: String): Option[CRS] =
    for {
      matrixSet <- matrixSetLookup.get(tileMatrixSetId)
    } yield matrixSet.supportedCrs
}
