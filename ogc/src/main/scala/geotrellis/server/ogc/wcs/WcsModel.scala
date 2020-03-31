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
import geotrellis.server.ogc.params.ParamMap
import geotrellis.server.ogc.utils._

import com.azavea.maml.ast.Expression

/** This class holds all the information necessary to construct a response to a WCS request */
case class WcsModel(
  serviceMetadata: ows.ServiceMetadata,
  sources: OgcSourceRepository,
  extendedParametersBinding: Option[ParamMap => Option[Expression => Expression]] = None
) {

  private val logger = org.log4s.getLogger

  def getLayers(p: GetCoverageWcsParams): List[OgcLayer] = {
    val filteredSources = sources.find(p.toQuery)
    logger.debug(s"Filtering sources: ${sources.store.length} -> ${filteredSources.length}")
    filteredSources.map {
        case SimpleSource(name, title, source, _, _, resampleMethod) =>
          SimpleOgcLayer(name, title, p.crs, source, None, resampleMethod)
        case gts @ GeoTrellisOgcSource(name, title, _, _, _, resampleMethod, _) =>
          val source = if (p.temporalSequence.nonEmpty) {
            gts.sourceForTime(p.temporalSequence.head)
          } else {
            gts.source
          }
          SimpleOgcLayer(name, title, p.crs, source, None, resampleMethod)
        case MapAlgebraSource(name, title, sources, algebra, _, _, resampleMethod) =>
          val simpleLayers = sources.mapValues { rs =>
            SimpleOgcLayer(name, title, p.crs, rs, None, resampleMethod)
          }
          val extendedParameters = extendedParametersBinding.flatMap(_.apply(p.params))
          MapAlgebraOgcLayer(name, title, p.crs, simpleLayers, algebra.bindExtendedParameters(extendedParameters), None, resampleMethod)
      }
  }
}
