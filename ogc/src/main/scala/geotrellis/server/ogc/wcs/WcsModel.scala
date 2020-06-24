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
import cats.Functor
import cats.syntax.functor._
import geotrellis.store.query.RepositoryM

/** This class holds all the information necessary to construct a response to a WCS request */
case class WcsModel[F[_]: Functor](
  serviceMetadata: ows.ServiceMetadata,
  sources: RepositoryM[F, List, OgcSource],
  extendedParametersBinding: Option[ParamMap => Option[Expression => Expression]] = None
) {
  def getLayers(p: GetCoverageWcsParams): F[List[OgcLayer]] = {
    val filteredSources = sources.find(p.toQuery)
    filteredSources.map {
      _.map {
        case SimpleSource(name, title, source, _, _, resampleMethod, overviewStrategy, _)               =>
          SimpleOgcLayer(name, title, p.crs, source, None, resampleMethod, overviewStrategy)
        case gts @ GeoTrellisOgcSource(name, title, _, _, _, resampleMethod, overviewStrategy, _)       =>
          val source =
            p.temporalSequence.headOption match {
              case Some(t)                                                  => gts.sourceForTime(t)
              case _ if p.temporalSequence.isEmpty && gts.source.isTemporal =>
                gts.sourceForTime(gts.source.times.head)
              case _                                                        => gts.source
            }
          SimpleOgcLayer(name, title, p.crs, source, None, resampleMethod, overviewStrategy)
        case MapAlgebraSource(name, title, sources, algebra, _, _, resampleMethod, overviewStrategy, _) =>
          val simpleLayers       = sources.mapValues { rs =>
            SimpleOgcLayer(name, title, p.crs, rs, None, resampleMethod, overviewStrategy)
          }
          val extendedParameters = extendedParametersBinding.flatMap(_.apply(p.params))
          MapAlgebraOgcLayer(
            name,
            title,
            p.crs,
            simpleLayers,
            algebra.bindExtendedParameters(extendedParameters),
            None,
            resampleMethod,
            overviewStrategy
          )
      }
    }
  }
}
