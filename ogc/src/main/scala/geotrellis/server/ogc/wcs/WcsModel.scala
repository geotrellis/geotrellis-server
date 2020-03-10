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

/** This class holds all the information necessary to construct a response to a WCS request */
case class WcsModel(
  serviceMetadata: ows.ServiceMetadata,
  sources: OgcSourceRepository
) {

  private val logger = org.log4s.getLogger

  def getLayers(p: GetCoverageWcsParams): List[OgcLayer] = {
    val filteredSources = sources.find(p.toQuery)
    logger.debug(s"Filtering sources: ${sources.store.length} -> ${filteredSources.length}")
    filteredSources.map {
        case SimpleSource(name, title, source, _, styles) =>
          SimpleOgcLayer(name, title, p.crs, source, None)
        case MapAlgebraSource(name, title, sources, algebra, _, styles) =>
          val simpleLayers = sources.mapValues { rs => SimpleOgcLayer(name, title, p.crs, rs, None) }
          MapAlgebraOgcLayer(name, title, p.crs, simpleLayers, algebra, None)
      }
  }
}
