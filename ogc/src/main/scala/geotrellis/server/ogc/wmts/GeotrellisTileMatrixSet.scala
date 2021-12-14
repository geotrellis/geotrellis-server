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

import geotrellis.server.ogc.URN
import geotrellis.proj4.CRS
import geotrellis.vector.Extent

import opengis.ows._
import opengis.wmts.TileMatrixSet

import java.net.URI

/** A collection of tile matrices; most commonly forming a pyramid of different resolutions */
case class GeotrellisTileMatrixSet(
    identifier: String,
    supportedCrs: CRS,
    title: Option[String] = None,
    `abstract`: Option[String] = None,
    boundingBox: Option[Extent] = None,
    wellKnownScaleSet: Option[String] = None,
    tileMatrix: List[GeotrellisTileMatrix]
) {
  def toXml: TileMatrixSet = {
    val ret = TileMatrixSet(
      Title = title.map(LanguageStringType(_)).toList,
      Abstract = `abstract`.map(LanguageStringType(_)).toList,
      Keywords = Nil,
      Identifier = CodeType(identifier),
      TileMatrix = tileMatrix.map(_.toXml(supportedCrs)),
      SupportedCRS = new URI(URN.unsafeFromCrs(supportedCrs))
    )

    if (wellKnownScaleSet.isDefined) ret.copy(WellKnownScaleSet = wellKnownScaleSet.map(new URI(_)))
    else ret.copy(BoundingBox = ???)
  }
}
