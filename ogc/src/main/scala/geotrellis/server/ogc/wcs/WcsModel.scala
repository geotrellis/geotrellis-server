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

package geotrellis.server.ogc.wcs

import geotrellis.server.ogc._
import higherkindness.droste.scheme

/** This class holds all the information necessary to construct a response to a WCS request */
case class WcsModel(
  serviceMetadata: ows.ServiceMetadata,
  sources: OgcSourceCollection
) {

  def getLayers(p: GetCoverageWcsParams): List[OgcLayer] = {
    /*scheme
      .cata(OgcSourceCollection.algebgra)
      .apply(p.toQuery)(sources.list)*/
      // unfold JSON directly and fold it into the QueryF
      // .hylo(OgcSourceCollection.algebgra, p.colagebra)
      // .apply(p)(sources.list)
    sources
      .find(p.toQuery)
      .map {
        case SimpleSource(name, title, source, styles) =>
          SimpleOgcLayer(name, title, p.crs, source, None)
        case MapAlgebraSource(name, title, sources, algebra, styles) =>
          val simpleLayers = sources.mapValues { rs => SimpleOgcLayer(name, title, p.crs, rs, None) }
          MapAlgebraOgcLayer(name, title, p.crs, simpleLayers, algebra, None)
      }
  }
}
